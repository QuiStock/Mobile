package com.quistock.quistock.data.remote.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LogoutCsrfTests {
    @Test fun csrfCookieIsScopedToLogoutAndDiscardedAfterAttempt() {
        val origin = "https://auth.example.test/".toHttpUrl()
        val csrfUrl = requireNotNull(origin.resolve("/csrf"))
        val logoutUrl = requireNotNull(origin.resolve("/logout"))
        val cookie = requireNotNull(Cookie.parse(csrfUrl, "AUTH-XSRF-TOKEN=synthetic; Path=/; HttpOnly; Secure"))
        val jar = StagedAuthCookieJar(origin, emptyList(), System::currentTimeMillis, "/logout")
        jar.saveFromResponse(csrfUrl, listOf(cookie))
        assertTrue(jar.hasCsrf(logoutUrl))
        for (url in listOf("/csrf", "/refresh", "/login")) {
            assertTrue(jar.loadForRequest(requireNotNull(origin.resolve(url))).isEmpty())
        }
        assertTrue(jar.loadForRequest("https://auth.example.test:444/logout".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://core.example.test/logout".toHttpUrl()).isEmpty())
        assertTrue(jar.received().isEmpty())
        jar.discardCsrf()
        assertFalse(jar.hasCsrf(logoutUrl))
        assertTrue(jar.loadForRequest(logoutUrl).isEmpty())
    }

    @Test fun invalidPreparationsNeverPostOrRemoveLiveCredentials() = runBlocking {
        val invalid = listOf(
            csrfResponse().setResponseCode(403),
            csrfResponse().setResponseCode(429),
            csrfResponse().setResponseCode(503),
            csrfResponse().setBody("{}"),
            csrfResponse().setBody("""{"headerName":"Authorization","token":"synthetic"}"""),
            csrfResponse().removeHeader("Set-Cookie"),
            csrfResponse().setHeader("Set-Cookie", "AUTH-XSRF-TOKEN=synthetic; Path=/refresh; HttpOnly"),
            csrfResponse().addHeader("Set-Cookie", "AUTH-XSRF-TOKEN=duplicate; Path=/logout; HttpOnly"),
        )
        for (response in invalid) {
            AuthHttpFixture().use { f ->
                f.login()
                f.auth.enqueue(response)
                assertFalse(f.repository.prepareLogout(f.session.snapshot().generation).execute())
                assertEquals(2, f.auth.requestCount)
                assertEquals("old-refresh", f.store.record!!.value)
            }
        }
    }

    @Test fun refreshExpiringDuringCsrfNeverPosts() = runBlocking {
        var now = System.currentTimeMillis()
        AuthHttpFixture { now }.use { f ->
            f.login()
            val operation = f.repository.prepareLogout(f.session.snapshot().generation)
            f.auth.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    now = f.store.record!!.expiresAt
                    return csrfResponse()
                }
            }
            assertFalse(operation.execute())
            assertEquals(2, f.auth.requestCount)
        }
    }

    @Test fun localRevocationCompletesWhileCsrfIsPending() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            val operation = f.repository.prepareLogout(f.session.snapshot().generation)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.auth.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/csrf" -> {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        csrfResponse()
                    }

                    else -> MockResponse().setResponseCode(204)
                }
            }
            val remote = async(Dispatchers.IO) { operation.execute() }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.repository.begin(100)
                f.repository.clear(100)
                assertNull(f.store.record)
                release.countDown()
                assertTrue(remote.await())
                assertNull(f.store.record)
                assertFalse(f.repository.hasAccess(100))
                assertEquals(3, f.auth.requestCount)
            } finally {
                release.countDown()
            }
        }
    }

    @Test fun forbiddenLogoutDoesNotRetryCsrfOrPost() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(MockResponse().setResponseCode(403))
            assertFalse(f.repository.prepareLogout(f.session.snapshot().generation).execute())
            assertEquals(3, f.auth.requestCount)
        }
    }

    @Test fun csrfAndLogoutShareOneTimeoutBudget() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse().setBodyDelay(4, TimeUnit.SECONDS))
            f.auth.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val started = System.nanoTime()
            assertFalse(f.repository.prepareLogout(f.session.snapshot().generation).execute())
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue(elapsed >= 9000 && elapsed < 13000)
            assertEquals(3, f.auth.requestCount)
        }
    }

    @Test fun csrfTimeoutDoesNotPost() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertFalse(f.repository.prepareLogout(f.session.snapshot().generation).execute())
            assertEquals(2, f.auth.requestCount)
        }
    }
}
