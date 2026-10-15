package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RetrofitAuthRepositoryTests {
    @Test fun reopeningRestoresRefreshCookieAndRenewsSessionAtCurrentRoute() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            val recreatedCustody = AuthCookieCustody(f.store, f.auth.url("/"))
            val recreatedRepository = RetrofitAuthRepository(f.auth.url("/"), recreatedCustody)
            val recreatedSession = com.quistock.quistock.domain.usecase.SessionUseCase(recreatedRepository)
            f.auth.enqueue(cookieResponse("renewed"))

            assertEquals(SessionState.Active, recreatedSession.restore())
            val refresh = f.auth.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/refresh", refresh.path)
            assertEquals("refresh_token=old-refresh", refresh.getHeader("Cookie"))
            assertEquals("renewed", recreatedCustody.access(recreatedSession.snapshot().generation))
            assertEquals("renewed-refresh", f.store.record!!.value)
        }
    }

    @Test fun incompleteSuccessAndCookiesWithoutExpiryCannotAuthenticate() = runBlocking {
        AuthHttpFixture().use { f ->
            f.auth.enqueue(MockResponse().addHeader("Set-Cookie", "access_token=synthetic; HttpOnly; Max-Age=300"))
            assertEquals(LoginError.UnexpectedError, f.session.login("synthetic", "synthetic"))
            assertNull(f.store.record)
            f.auth.enqueue(
                MockResponse()
                    .addHeader("Set-Cookie", "access_token=synthetic; HttpOnly; Max-Age=300")
                    .addHeader("Set-Cookie", "refresh_token=synthetic; HttpOnly"),
            )
            assertEquals(LoginError.UnexpectedError, f.session.login("synthetic", "synthetic"))
            assertEquals(SessionState.LocalFailure, f.session.state.value)
        }
    }

    @Test fun delayedHttpCookiesCannotReplaceNewLoginInMemoryOrDisk() = runBlocking {
        for (late in listOf(
            cookieResponse("late"),
            MockResponse().setResponseCode(503)
                .addHeader("Set-Cookie", "refresh_token=; HttpOnly; Path=/; Max-Age=0"),
        )) {
            AuthHttpFixture().use { f ->
                f.login()
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                f.auth.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = if (request.path == "/refresh") {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        late
                    } else {
                        cookieResponse("new-account")
                    }
                }
                val old = async(Dispatchers.IO) { f.session.refresh(f.session.snapshot()) }
                try {
                    assertTrue(entered.await(10, TimeUnit.SECONDS))
                    assertEquals(AuthResult.Success, f.session.login("new", "synthetic"))
                    release.countDown()
                    old.await()
                    assertEquals("new-account", f.custody.access(f.session.snapshot().generation))
                    val recreated = AuthCookieCustody(f.store, f.auth.url("/"))
                    recreated.begin(99)
                    assertEquals(RestoreCookies.AVAILABLE, recreated.restore(99))
                    assertEquals(
                        "new-account-refresh",
                        recreated.requestJar(99, true)
                            .loadForRequest(f.auth.url("/refresh")).single().value,
                    )
                } finally {
                    release.countDown()
                }
            }
        }
    }

    @Test fun errorResponseDeletionIsAppliedRatherThanAssumingPreservation() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(
                MockResponse().setResponseCode(503)
                    .addHeader("Set-Cookie", "refresh_token=; HttpOnly; Path=/; Max-Age=0"),
            )
            f.session.refresh(f.session.snapshot())
            assertNull(f.store.record)
        }
    }

    @Test fun retryAfterBlocksNetworkCallsWithoutAutomaticRetry() = runBlocking {
        AuthHttpFixture().use { f ->
            f.auth.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
            assertEquals(LoginError.RateLimited, f.session.login("synthetic", "synthetic"))
            assertTrue(f.repository.retryDelayMillis() > 0)
            assertEquals(LoginError.RateLimited, f.session.login("synthetic", "synthetic"))
            assertEquals(1, f.auth.requestCount)
        }
    }

    @Test fun loginErrorsUseDocumentedCodesAndRefreshRejectionEndsSession() = runBlocking {
        AuthHttpFixture().use { f ->
            f.auth.enqueue(MockResponse().setResponseCode(403).setBody("{\"code\":\"platform_not_allowed\"}"))
            assertEquals(LoginError.PlatformNotAllowed, f.session.login("synthetic", "synthetic"))
            f.auth.enqueue(MockResponse().setResponseCode(401).setBody("{\"code\":\"invalid_credentials\"}"))
            assertEquals(LoginError.InvalidCredentials, f.session.login("synthetic", "synthetic"))
            f.login()
            f.auth.enqueue(MockResponse().setResponseCode(401).setBody("{\"code\":\"invalid_refresh_token\"}"))
            f.session.refresh(f.session.snapshot())
            assertEquals(SessionState.Expired, f.session.state.value)
            assertNull(f.store.record)
        }
    }

    @Test fun accessExpiryRefreshesBeforeProtectedOperationWithoutTimer() = runBlocking {
        MockWebServer().use { server ->
            var now = System.currentTimeMillis()
            val store = MemoryCookieStore()
            val custody = AuthCookieCustody(store, server.url("/")) { now }
            val repository = RetrofitAuthRepository(server.url("/"), custody)
            custody.begin(1)
            val parsed = Cookie.parseAll(server.url("/"), cookieResponse("synthetic").headers)
            custody.commit(1, parsed, true)
            now = parsed.first { it.name == "access_token" }.expiresAt
            assertFalse(repository.hasAccess(1))
            assertEquals(0, server.requestCount)
            server.enqueue(cookieResponse("new", 600))
            assertEquals(AuthResult.Success, repository.refresh(1))
            assertTrue(repository.hasAccess(1))
        }
    }
}
