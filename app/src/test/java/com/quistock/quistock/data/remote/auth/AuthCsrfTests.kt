package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AuthCsrfTests {
    @Test fun latePreparationCannotPostForAnOldGeneration() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.auth.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/csrf" -> {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        csrfResponse()
                    }

                    "/login" -> cookieResponse("new")

                    else -> MockResponse().setResponseCode(500)
                }
            }
            val old = async(Dispatchers.IO) { f.session.refresh(f.session.snapshot()) }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                assertEquals(AuthResult.Success, f.session.login("new", "synthetic"))
                release.countDown()
                old.await()
                assertEquals("new-refresh", f.store.record!!.value)
                assertEquals(SessionState.Active, f.session.state.value)
                assertEquals(3, f.auth.requestCount)
            } finally {
                release.countDown()
            }
        }
    }

    @Test fun cancelledPreparationDoesNotPostOrRevokeRefresh() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.auth.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    return csrfResponse()
                }
            }
            val pending = async(Dispatchers.IO) { f.session.refresh(f.session.snapshot()) }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                pending.cancel()
                pending.join()
                assertEquals("old-refresh", f.store.record!!.value)
                assertEquals(2, f.auth.requestCount)
            } finally {
                release.countDown()
            }
        }
    }

    @Test fun refreshExpiringDuringPreparationNeverPosts() = runBlocking {
        var now = System.currentTimeMillis()
        AuthHttpFixture { now }.use { f ->
            f.login()
            f.auth.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    now = f.store.record!!.expiresAt
                    return csrfResponse()
                }
            }
            f.session.refresh(f.session.snapshot())
            assertEquals(SessionState.Expired, f.session.state.value)
            assertEquals(2, f.auth.requestCount)
            assertNull(f.store.record)
        }
    }

    @Test fun unavailablePreparationDoesNotRetryEvenWithImmediateRetryAfter() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            f.session.refresh(f.session.snapshot())
            assertEquals(SessionState.Failure(SessionFailure.SERVER), f.session.state.value)
            assertNotNull(f.store.record)
            assertEquals(2, f.auth.requestCount)
        }
    }

    @Test fun unavailablePostPreservesCookieDeletionWithoutHttpFollowUp() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(
                MockResponse().setResponseCode(503).setHeader("Retry-After", "0")
                    .addHeader("Set-Cookie", "refresh_token=; Path=/; HttpOnly; Max-Age=0"),
            )
            f.session.refresh(f.session.snapshot())
            assertEquals(SessionState.Failure(SessionFailure.SERVER), f.session.state.value)
            assertEquals(3, f.auth.requestCount)
            assertNull(f.store.record)
        }
    }

    @Test fun refreshSendsJsonTokenAndBothCookiesWithoutPersistingCsrf() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(cookieResponse("renewed"))
            assertEquals(AuthResult.Success, f.repository.refresh(f.session.snapshot().generation))
            val get = f.auth.takeRequest()
            assertEquals("/csrf", get.path)
            assertNull(get.getHeader("Cookie"))
            assertNull(get.getHeader("Authorization"))
            val post = f.auth.takeRequest()
            assertEquals("/refresh", post.path)
            assertEquals("masked-synthetic", post.getHeader("X-CSRF-TOKEN"))
            assertEquals("refresh_token=old-refresh; AUTH-XSRF-TOKEN=raw-synthetic", post.getHeader("Cookie"))
            assertNull(post.getHeader("Authorization"))
            assertEquals(0L, post.bodySize)
            assertEquals(listOf("refresh_token"), f.store.records.map { it.name })
        }
    }

    @Test fun incompletePreparationNeverPostsOrDeletesRefresh() = runBlocking {
        for (response in listOf(
            MockResponse().setBody("{}"),
            csrfResponse().setBody("invalid-json"),
            csrfResponse().setBody("{\"headerName\":\"Authorization\",\"token\":\"synthetic\"}"),
            csrfResponse().setHeader("Set-Cookie", "AUTH-XSRF-TOKEN=synthetic; HttpOnly; Path=/other"),
            csrfResponse().setHeader("Set-Cookie", "AUTH-XSRF-TOKEN=; HttpOnly; Path=/; Max-Age=0"),
            csrfResponse().addHeader("Set-Cookie", "AUTH-XSRF-TOKEN=another; HttpOnly; Path=/"),
        )) {
            AuthHttpFixture().use { f ->
                f.login()
                f.auth.enqueue(response)
                f.session.refresh(f.session.snapshot())
                assertEquals(SessionState.Failure(SessionFailure.PROTOCOL), f.session.state.value)
                assertNotNull(f.store.record)
                assertEquals(2, f.auth.requestCount)
            }
        }
    }

    @Test fun publicCsrfUnauthorizedIsNotRefreshRejection() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(MockResponse().setResponseCode(401))
            f.session.refresh(f.session.snapshot())
            assertEquals(SessionState.Failure(SessionFailure.PROTOCOL), f.session.state.value)
            assertNotNull(f.store.record)
            assertEquals(2, f.auth.requestCount)
        }
    }

    @Test fun forbiddenRefreshPreservesCredentialsAndManualRetryObtainsNewPair() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(MockResponse().setResponseCode(403))
            f.session.refresh(f.session.snapshot())
            assertEquals(SessionState.Failure(SessionFailure.PROTOCOL), f.session.state.value)
            assertEquals("old-refresh", f.store.record!!.value)
            assertTrue(runCatching { f.session.requireAccess(f.session.snapshot()) }.isFailure)
            f.auth.enqueue(csrfResponse())
            f.auth.enqueue(cookieResponse("recovered"))
            f.session.refresh(f.session.snapshot())
            assertEquals(SessionState.Active, f.session.state.value)
            assertEquals(5, f.auth.requestCount)
        }
    }

    @Test fun csrfRateLimitBlocksSubsequentRequests() = runBlocking {
        AuthHttpFixture().use { f ->
            f.login()
            f.auth.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
            f.session.refresh(f.session.snapshot())
            assertEquals(SessionState.Failure(SessionFailure.RATE_LIMIT), f.session.state.value)
            f.session.refresh(f.session.snapshot())
            assertEquals(2, f.auth.requestCount)
            assertNotNull(f.store.record)
        }
    }
}

fun csrfResponse(): MockResponse = MockResponse()
    .setHeader("Content-Type", "application/json")
    .addHeader("Set-Cookie", "AUTH-XSRF-TOKEN=raw-synthetic; Path=/; HttpOnly")
    .setBody("{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"masked-synthetic\"}")
