package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.app.di.createAuthApi
import com.quistock.quistock.app.di.createCoreHttpClient
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
    @Test fun offlineReadOnlyCannotSendOrAutomaticallyRenewACoreRequest() {
        val snapshot = SessionSnapshot(1, false)
        val session = activeSession(snapshot)
        every {
            session.state
        } returns MutableStateFlow<SessionState>(
            SessionState.OfflineReadOnly(com.quistock.quistock.domain.model.SessionFailure.NETWORK),
        )
        val custody = mockk<AuthCookieCustody>()
        val origin = "https://core.example.test/".toHttpUrl()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns Request.Builder().url(origin).build()
        assertTrue(
            runCatching { CoreSessionInterceptor(origin, session, custody).intercept(chain) }
                .exceptionOrNull() is SessionException,
        )
        verify(exactly = 0) { chain.proceed(any()) }
        coVerify(exactly = 0) { session.refresh(any()) }
    }

    @Test fun confirmedStoreLossExpiresTheSessionWithoutRefreshingAndPreservesTheErrorBody() {
        val snapshot = SessionSnapshot(1, true)
        val session = activeSession(snapshot)
        coEvery { session.expire(snapshot) } returns Unit
        val custody = mockk<AuthCookieCustody>()
        every { custody.access(any()) } returns "fixture"
        val origin = "https://core.example.test/".toHttpUrl()
        val request = Request.Builder().url(origin).build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(403).message("fixture").body("""{"error":"NO_ACTIVE_STORE"}""".toResponseBody()).build()
        CoreSessionInterceptor(origin, session, custody).intercept(chain).use {
            assertEquals("""{"error":"NO_ACTIVE_STORE"}""", it.body!!.string())
        }
        coVerify(exactly = 1) { session.expire(snapshot) }
        coVerify(exactly = 0) { session.refresh(any()) }
    }

    @Test fun missingAccessCookieFailsAsSessionExceptionWithoutSendingRequest() {
        val snapshot = SessionSnapshot(1, true)
        val session = activeSession(snapshot)
        val custody = mockk<AuthCookieCustody>()
        every { custody.access(any()) } returns null
        val origin = "https://core.example.test/".toHttpUrl()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns Request.Builder().url(origin).build()

        val failure = runCatching {
            CoreSessionInterceptor(origin, session, custody).intercept(chain)
        }.exceptionOrNull()

        assertTrue(failure is SessionException)
        verify(exactly = 0) { chain.proceed(any()) }
        coVerify(exactly = 0) { session.refresh(any()) }
    }

    @Test fun missingAccessCookieAfterRefreshDoesNotSendRetryOrCrashNetworkThread() {
        val snapshot = SessionSnapshot(1, true)
        val session = activeSession(snapshot)
        coEvery { session.refresh(snapshot) } returns snapshot.copy(revision = 1)
        val custody = mockk<AuthCookieCustody>()
        every { custody.access(any()) } returnsMany listOf("fixture", null)
        val origin = "https://core.example.test/".toHttpUrl()
        val request = Request.Builder().url(origin).build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(401).message("fixture")
            .body("".toResponseBody()).build()

        val failure = runCatching {
            CoreSessionInterceptor(origin, session, custody).intercept(chain)
        }.exceptionOrNull()

        assertTrue(failure is SessionException)
        verify(exactly = 1) { chain.proceed(any()) }
        coVerify(exactly = 1) { session.refresh(snapshot) }
    }

    @Test
    fun actionWriteRenewsUnauthorizedSessionWithoutReplayingTheRequest() {
        val snapshot = SessionSnapshot(1, true)
        val session = activeSession(snapshot)
        coEvery { session.refresh(snapshot) } returns snapshot.copy(revision = 1)
        val custody = mockk<AuthCookieCustody>()
        every { custody.access(any()) } returns "fixture"
        val origin = "https://core.example.test/".toHttpUrl()
        val request = Request.Builder().url(origin).post("{}".toRequestBody()).build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(401).message("fixture")
            .body("".toResponseBody()).build()
        CoreSessionInterceptor(origin, session, custody, replayWrites = false).intercept(chain).use {
            assertEquals(401, it.code)
        }
        verify(exactly = 1) { chain.proceed(any()) }
        coVerify(exactly = 1) { session.refresh(snapshot) }
    }

    private fun activeSession(snapshot: SessionSnapshot): SessionUseCase {
        val session = mockk<SessionUseCase>()
        every { session.state } returns MutableStateFlow<SessionState>(SessionState.Active)
        every { session.snapshot() } returns snapshot
        coEvery { session.requireAccess(any()) } coAnswers { firstArg<SessionSnapshot>() }
        return session
    }

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
            val request = Request.Builder().url(f.core.url("/fixture"))
                .header("Authorization", "Bearer stale-fixture")
                .header("Cookie", "refresh_token=fixture; AUTH-XSRF-TOKEN=fixture")
                .header("X-CSRF-TOKEN", "fixture")
                .header("X-XSRF-TOKEN", "fixture")
                .build()
            f.client.newCall(request).execute().use { assertEquals(200, it.code) }
            val original = f.core.takeRequest()
            assertEquals("Bearer old", original.getHeader("Authorization"))
            assertNull(original.getHeader("Cookie"))
            assertNull(original.getHeader("X-CSRF-TOKEN"))
            assertNull(original.getHeader("X-XSRF-TOKEN"))
            val retried = f.core.takeRequest()
            assertEquals("Bearer new", retried.getHeader("Authorization"))
            assertNull(retried.getHeader("Cookie"))
            assertNull(retried.getHeader("X-CSRF-TOKEN"))
            assertNull(retried.getHeader("X-XSRF-TOKEN"))
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
