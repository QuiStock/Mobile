package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.app.di.createCoreHttpClient
import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.AuthTokens
import com.quistock.quistock.domain.model.RefreshToken
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.SecretStorage
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CoreSessionInterceptorTests {
    private val auth = mockk<AuthRepository>()
    private val storage = mockk<SecretStorage>(relaxed = true)
    private val cache = mockk<CachedBigNumbersRepository>(relaxed = true)
    private val session = SessionUseCase(auth, storage, cache)
    private val initial = AuthTokens(AccessToken("old-synthetic"), RefreshToken("opaque"))
    private val renewed = AuthTokens(AccessToken("new-synthetic"), RefreshToken("rotated"))

    @Test fun unauthorizedRefreshesAndRetriesWithStoredToken() = runBlocking {
        MockWebServer().use { server ->
            session.install(initial)
            coEvery { auth.refresh(initial.refreshToken) } returns AuthResult.Success(renewed)
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setBody("ok"))
            execute(server).use { assertEquals(200, it.code) }
            assertEquals("Bearer old-synthetic", server.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer new-synthetic", server.takeRequest().getHeader("Authorization"))
            coVerify(exactly = 1) { storage.save(renewed.accessToken, AccessToken::class) }
            coVerify(exactly = 1) { auth.refresh(initial.refreshToken) }
        }
    }

    @Test fun secondUnauthorizedExpiresAndClearsCacheWithoutLoop() = runBlocking {
        MockWebServer().use { server ->
            session.install(initial)
            coEvery { auth.refresh(any()) } returns AuthResult.Success(renewed)
            repeat(2) { server.enqueue(MockResponse().setResponseCode(401)) }
            assertTrue(runCatching { execute(server) }.isFailure)
            assertEquals(2, server.requestCount)
            assertEquals(SessionState.Expired, session.state.value)
            coVerify(exactly = 1) { auth.refresh(any()) }
            coVerify(exactly = 2) { cache.clear() }
        }
    }

    @Test fun otherOriginDoesNotReceiveBearer() = runBlocking {
        MockWebServer().use { core ->
            MockWebServer().use { other ->
                session.install(initial)
                other.enqueue(MockResponse())
                val client = OkHttpClient.Builder().addInterceptor(
                    CoreSessionInterceptor(core.url("/"), session),
                ).build()
                client.newCall(Request.Builder().url(other.url("/fixture")).build()).execute().close()
                assertNull(other.takeRequest().getHeader("Authorization"))
            }
        }
    }

    @Test fun nonUnauthorizedResponsesNeverRefresh() = runBlocking {
        MockWebServer().use { server ->
            session.install(initial)
            for (code in listOf(200, 403, 500)) {
                server.enqueue(MockResponse().setResponseCode(code))
                execute(server).use { assertEquals(code, it.code) }
            }
            coVerify(exactly = 0) { auth.refresh(any()) }
        }
    }

    @Test fun transientRefreshPreservesSessionAndDoesNotRetryRequest() = runBlocking {
        MockWebServer().use { server ->
            session.install(initial)
            coEvery { auth.refresh(any()) } returns AuthRefreshError.ServerError
            server.enqueue(MockResponse().setResponseCode(401))
            assertTrue(runCatching { execute(server) }.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals(initial.accessToken, session.snapshot().accessToken)
            coVerify(exactly = 1) { cache.clear() }
            coVerify(exactly = 0) { storage.delete(RefreshToken::class) }
        }
    }

    @Test fun simultaneousUnauthorizedRequestsShareRefreshAndEachRetryOnce() = runBlocking {
        MockWebServer().use { server ->
            session.install(initial)
            coEvery { auth.refresh(any()) } returns AuthResult.Success(renewed)
            val barrier = CyclicBarrier(2)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.getHeader("Authorization") == "Bearer old-synthetic") {
                        barrier.await(5, TimeUnit.SECONDS)
                        MockResponse().setResponseCode(401)
                    } else {
                        MockResponse().setBody("ok")
                    }
            }
            val executor = Executors.newFixedThreadPool(2)
            try {
                val requests = List(2) { executor.submit<Int> { execute(server).use { it.code } } }
                requests.forEach { assertEquals(200, it.get(5, TimeUnit.SECONDS)) }
                assertEquals(4, server.requestCount)
                coVerify(exactly = 1) { auth.refresh(any()) }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test fun coreClientDoesNotFollowRedirectsToAnotherOrigin() = runBlocking {
        MockWebServer().use { core ->
            MockWebServer().use { other ->
                session.install(initial)
                core.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/fixture")))
                val client = createCoreHttpClient(core.url("/").toString(), session)
                client.newCall(Request.Builder().url(core.url("/fixture")).build()).execute().use {
                    assertEquals(302, it.code)
                }
                assertEquals(0, other.requestCount)
            }
        }
    }

    @Test fun operationWaitingForRecoveryCannotUseANewLoginIdentity() = runBlocking {
        coEvery { storage.read(RefreshToken::class) } returns initial.refreshToken
        coEvery { auth.refresh(any()) } returns AuthRefreshError.NetworkError
        session.restore()
        val started = CountDownLatch(1)
        val pending = CompletableDeferred<AuthRefreshResult>()
        coEvery { auth.refresh(any()) } coAnswers {
            started.countDown()
            pending.await()
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            val executor = Executors.newSingleThreadExecutor()
            try {
                val operation = executor.submit<Boolean> { runCatching { execute(server).close() }.isFailure }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                session.install(renewed)
                pending.complete(AuthResult.Success(initial))
                assertTrue(operation.get(5, TimeUnit.SECONDS))
                assertEquals(0, server.requestCount)
                assertEquals(renewed.accessToken, session.snapshot().accessToken)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun execute(server: MockWebServer): okhttp3.Response {
        val client = OkHttpClient.Builder().addInterceptor(CoreSessionInterceptor(server.url("/"), session)).build()
        return client.newCall(Request.Builder().url(server.url("/fixture")).build()).execute()
    }
}
