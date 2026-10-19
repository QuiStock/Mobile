package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.app.di.createAuthApi
import com.quistock.quistock.app.di.createCoreHttpClient
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CoreSessionInterceptorTests {
    @Test fun failedRenewalCannotBeRetriedByAnotherProtectedRequest() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(MockResponse().setResponseCode(403))
            f.core.enqueue(MockResponse().setResponseCode(401))
            assertTrue(runCatching { f.execute().close() }.isFailure)
            assertEquals(SessionState.Failure(SessionFailure.PROTOCOL), f.session.state.value)
            assertTrue(runCatching { f.execute().close() }.isFailure)
            assertEquals(3, f.auth.requestCount)
            assertEquals(1, f.core.requestCount)
            assertNotNull(f.store.record)
        }
    }

    @Test fun expiredAccessRenewsBeforeFirstProtectedRequestWithoutIdleCalls() = runBlocking {
        var now = System.currentTimeMillis()
        AuthHttpFixture { now }.use { f ->
            f.login()
            now += 301000
            assertEquals(1, f.auth.requestCount)
            assertFalse(f.session.snapshot().hasAccess)
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(cookieResponse("renewed", 600))
            f.core.enqueue(MockResponse())
            f.execute().close()
            assertEquals(1, f.core.requestCount)
            assertTrue(f.core.takeRequest().getHeader("Authorization") == "Bearer renewed")
            assertEquals(3, f.auth.requestCount)
        }
    }

    @Test fun unauthorizedRefreshesAndRetriesWithoutSendingCookiesToCore() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(cookieResponse("new"))
            f.core.enqueue(MockResponse().setResponseCode(401))
            f.core.enqueue(MockResponse().setBody("ok"))
            f.execute().use { assertEquals(200, it.code) }
            assertEquals("Bearer old", f.core.takeRequest().getHeader("Authorization"))
            val retried = f.core.takeRequest()
            assertEquals("Bearer new", retried.getHeader("Authorization"))
            assertNull(retried.getHeader("Cookie"))
            assertEquals("/csrf", f.auth.takeRequest().path)
            val refresh = f.auth.takeRequest()
            assertEquals("/refresh", refresh.path)
            assertEquals("refresh_token=old-refresh; AUTH-XSRF-TOKEN=raw-synthetic", refresh.getHeader("Cookie"))
            assertEquals("masked-synthetic", refresh.getHeader("X-CSRF-TOKEN"))
            assertNull(refresh.getHeader("Authorization"))
            assertEquals(0L, refresh.bodySize)
        }
    }

    @Test fun secondUnauthorizedExpiresAndRevokesCredentialsWithoutLoop() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(cookieResponse("new"))
            repeat(2) { f.core.enqueue(MockResponse().setResponseCode(401)) }
            assertTrue(runCatching { f.execute().close() }.isFailure)
            assertEquals(SessionState.Expired, f.session.state.value)
            assertEquals(2, f.core.requestCount)
            assertNull(f.store.record)
        }
    }

    @Test fun otherOriginAndRedirectDoNotReceiveCredentials() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            MockWebServer().use { other ->
                other.enqueue(MockResponse())
                f.client.newCall(Request.Builder().url(other.url("/fixture")).build()).execute().close()
                val request = other.takeRequest()
                assertNull(request.getHeader("Cookie"))
                assertNull(request.getHeader("Authorization"))
                f.core.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/fixture")))
                f.execute().use { assertEquals(302, it.code) }
                assertEquals(1, other.requestCount)
            }
        }
    }

    @Test fun nonUnauthorizedResponsesNeverRefresh() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            for (code in listOf(200, 403, 500)) {
                f.core.enqueue(MockResponse().setResponseCode(code))
                f.execute().use { assertEquals(code, it.code) }
            }
            assertEquals(1, f.auth.requestCount)
        }
    }

    @Test fun transientRefreshPreservesSessionWithoutRetryingCore() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(MockResponse().setResponseCode(503))
            f.core.enqueue(MockResponse().setResponseCode(401))
            assertTrue(runCatching { f.execute().close() }.isFailure)
            assertEquals(SessionState.Failure(SessionFailure.SERVER), f.session.state.value)
            assertNotNull(f.store.record)
            assertEquals(1, f.core.requestCount)
        }
    }

    @Test fun concurrentUnauthorizedRequestsShareRefreshAndEachRetryOnce() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(cookieResponse("new"))
            val barrier = CyclicBarrier(2)
            f.core.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.getHeader("Authorization") == "Bearer old") {
                        barrier.await(10, TimeUnit.SECONDS)
                        MockResponse().setResponseCode(401)
                    } else {
                        MockResponse()
                    }
            }
            val executor = Executors.newFixedThreadPool(2)
            try {
                val calls = List(2) { executor.submit<Int> { f.execute().use { it.code } } }
                calls.forEach { assertEquals(200, it.get(15, TimeUnit.SECONDS)) }
                assertEquals(3, f.auth.requestCount)
                assertEquals(4, f.core.requestCount)
            } finally {
                executor.shutdownNow()
            }
        }
    }
}

fun cookieResponse(access: String = "synthetic", age: Int = 300): MockResponse = MockResponse()
    .addHeader("Set-Cookie", "access_token=" + access + "; Path=/; HttpOnly; Max-Age=" + age)
    .addHeader("Set-Cookie", "refresh_token=" + access + "-refresh; Path=/; HttpOnly; Max-Age=1296000")

class AuthHttpFixture(now: () -> Long = System::currentTimeMillis) : java.io.Closeable {
    val auth = MockWebServer()
    val core = MockWebServer()
    val store = MemoryCookieStore()
    val custody = AuthCookieCustody(store, auth.url("/"), now)
    val repository = RetrofitAuthRepository(auth.url("/"), custody, { createAuthApi(auth.url("/"), it) })
    val session = SessionUseCase(repository)
    val client = createCoreHttpClient(core.url("/").toString(), session, custody)
    suspend fun login() {
        auth.enqueue(cookieResponse("old"))
        assertEquals(AuthResult.Success, session.login("synthetic@example.com", "synthetic"))
        val request = auth.takeRequest()
        assertNull(request.getHeader("Cookie"))
        assertNull(request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"platform\":\"mobile\""))
    }
    fun execute() = client.newCall(Request.Builder().url(core.url("/fixture")).build()).execute()
    override fun close() {
        auth.shutdown()
        core.shutdown()
    }
}
