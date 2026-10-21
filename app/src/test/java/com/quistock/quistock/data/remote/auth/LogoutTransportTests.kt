package com.quistock.quistock.data.remote.auth

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LogoutTransportTests {
    @Test fun logoutUsesOnlyCapturedRefreshAndDoesNotCommitResponseCookies() = runTest {
        AuthHttpFixture().use { f ->
            f.login()
            val request = f.repository.prepareLogout(f.session.snapshot().generation)
            f.repository.begin(100)
            f.repository.clear(100)
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(cookieResponse("should-not-install").setResponseCode(204))
            assertTrue(request.execute())
            val preparation = f.auth.takeRequest()
            assertEquals("/csrf", preparation.path)
            assertNull(preparation.getHeader("Cookie"))
            val sent = f.auth.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/logout", sent.path)
            assertEquals(0L, sent.bodySize)
            assertEquals("refresh_token=old-refresh; AUTH-XSRF-TOKEN=raw-synthetic", sent.getHeader("Cookie"))
            assertEquals("masked-synthetic", sent.getHeader("X-CSRF-TOKEN"))
            assertNull(sent.getHeader("Authorization"))
            assertFalse(f.repository.hasAccess(100))
        }
    }

    @Test fun missingRefreshStillCallsLogoutAndAccepts204WithoutSetCookie() = runTest {
        AuthHttpFixture().use { f ->
            f.auth.enqueue(MockResponse().setResponseCode(204))
            assertTrue(f.repository.prepareLogout(0).execute())
            assertNull(f.auth.takeRequest().getHeader("Cookie"))
        }
    }

    @Test fun remoteFailureDoesNotInstallCookiesOrRetry() = runTest {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(cookieResponse("rejected").setResponseCode(429).addHeader("Retry-After", "60"))
            assertFalse(f.repository.prepareLogout(f.session.snapshot().generation).execute())
            assertEquals(3, f.auth.requestCount)
        }
    }

    @Test fun serviceUnavailableCannotTriggerHttpFollowUp() = runTest {
        AuthHttpFixture().use { f ->
            f.auth.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "0"))
            f.auth.enqueue(MockResponse().setResponseCode(204))
            assertFalse(f.repository.prepareLogout(0).execute())
            assertEquals(1, f.auth.requestCount)
        }
    }

    @Test fun expiredRefreshIsNotSent() = runTest {
        var clock = System.currentTimeMillis()
        AuthHttpFixture { clock }.use { f ->
            f.login()
            clock += TimeUnit.DAYS.toMillis(16)
            f.auth.enqueue(MockResponse().setResponseCode(204))
            assertTrue(f.repository.prepareLogout(f.session.snapshot().generation).execute())
            assertNull(f.auth.takeRequest().getHeader("Cookie"))
        }
    }

    @Test fun logoutTimeoutIsBoundedAndDoesNotRetry() = runTest {
        AuthHttpFixture().use { f ->
            f.auth.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val started = System.nanoTime()
            assertFalse(f.repository.prepareLogout(0).execute())
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue(elapsed >= 9000 && elapsed < 15000)
            assertEquals(1, f.auth.requestCount)
        }
    }

    @Test fun lateLogoutCannotRemoveCredentialsInstalledByNewLogin() = runTest {
        AuthHttpFixture().use { f ->
            f.login()
            val old = f.repository.prepareLogout(f.session.snapshot().generation)
            f.auth.enqueue(cookieResponse("new"))
            f.session.login("synthetic", "synthetic")
            f.auth.takeRequest()
            val replacement = f.session.snapshot()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(
                MockResponse().setResponseCode(204)
                    .addHeader("Set-Cookie", "refresh_token=; Max-Age=0; Path=/; HttpOnly"),
            )
            assertTrue(old.execute())
            assertEquals(replacement, f.session.snapshot())
            assertEquals("new", f.custody.access(replacement.generation))
        }
    }
}
