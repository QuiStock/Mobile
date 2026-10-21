package com.quistock.quistock.data.remote.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCookieCustodyTests {
    @Test fun csrfStaysInTheAttemptAndCannotInstallAuthenticationCookies() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        val jar = custody.requestJar(1, true)
        val csrf = Cookie.Builder().name(AuthCookieCustody.CSRF).value("synthetic-csrf")
            .hostOnlyDomain(origin.host).path("/").secure().httpOnly().build()
        jar.saveFromResponse(origin.resolve("/csrf")!!, listOf(csrf) + pair())
        assertTrue(jar.hasCsrf(origin.resolve("/refresh")!!))
        assertTrue(jar.received().isEmpty())
        assertEquals(2, jar.loadForRequest(origin.resolve("/refresh")!!).size)
        for (url in listOf(
            origin.resolve("/csrf")!!,
            origin.resolve("/login")!!,
            origin.resolve("/logout")!!,
            "https://auth.example.test:444/refresh".toHttpUrl(),
            "http://auth.example.test/refresh".toHttpUrl(),
        )) {
            assertTrue(jar.loadForRequest(url).isEmpty())
        }
        assertEquals(listOf("refresh_token"), store.records.map { it.name })
        assertFalse(custody.requestJar(1, true).hasCsrf(origin.resolve("/refresh")!!))
        jar.discardCsrf()
        assertFalse(jar.hasCsrf(origin.resolve("/refresh")!!))
    }

    private val origin = "https://auth.example.test/".toHttpUrl()
    private var time = 1000L
    private val store = MemoryCookieStore()
    private val custody = AuthCookieCustody(store, origin) { time }
    private fun cookie(name: String, value: String = "synthetic", expires: Long = 2000) = Cookie.Builder()
        .name(name).value(value).hostOnlyDomain(origin.host).path("/").secure().httpOnly().expiresAt(expires).build()
    private fun pair() = listOf(cookie("access_token"), cookie("refresh_token"))

    @Test fun logoutCaptureCannotSendRefreshToOtherRoutesOrOrigins() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        val jar = custody.requestJar(1, true, AuthCookieCustody.LOGOUT_PATH)
        assertEquals(listOf("refresh_token"), jar.loadForRequest(origin.resolve("/logout")!!).map { it.name })
        assertTrue(jar.loadForRequest(origin.resolve("/refresh")!!).isEmpty())
        assertTrue(jar.loadForRequest(origin.resolve("/login")!!).isEmpty())
        assertTrue(jar.loadForRequest("https://other.example.test/logout".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://auth.example.test/logout".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://auth.example.test:8443/logout".toHttpUrl()).isEmpty())
    }

    @Test fun cancellationAfterPersistenceStartsCompletesCommitBeforePropagation() = kotlinx.coroutines.test.runTest {
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var saved = emptyList<RefreshCookieRecord>()
        val delayed = object : AuthCookieStore {
            override suspend fun read() = saved
            override suspend fun write(records: List<RefreshCookieRecord>) {
                entered.complete(Unit)
                release.await()
                saved = records
            }
            override suspend fun revoke() {
                saved = emptyList()
            }
        }
        val isolated = AuthCookieCustody(delayed, origin) { time }
        isolated.begin(1)
        val task = async { isolated.commit(1, pair(), true) }
        entered.await()
        assertNull(isolated.access(1))
        task.cancel()
        release.complete(Unit)
        task.join()
        assertEquals(1, saved.size)
        assertNotNull(isolated.access(1))
    }

    @Test fun successFollowedByDeletionCannotPublishIncompletePair() = runBlocking {
        custody.begin(1)
        val batch = pair() + cookie("refresh_token", "", 0)
        assertTrue(runCatching { custody.commit(1, batch, true) }.isFailure)
        assertNull(custody.access(1))
        assertNull(store.record)
    }

    @Test fun expiryParsingHonorsMaxAgeOverExpires() {
        val cookie = Cookie.parse(
            origin,
            "refresh_token=synthetic; HttpOnly; Max-Age=0; Expires=Wed, 01 Jan 2031 00:00:00 GMT",
        )!!
        assertTrue(cookie.expiresAt <= System.currentTimeMillis())
        assertTrue(cookie.persistent)
    }

    @Test fun identitiesWithDifferentPathsSurviveRotationAndSelectiveDeletion() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        val scoped = Cookie.Builder().name("refresh_token").value("scoped-synthetic")
            .hostOnlyDomain(origin.host).path("/auth").secure().httpOnly().expiresAt(2000).build()
        custody.commit(1, listOf(scoped), false)
        assertEquals(2, store.records.size)
        custody.commit(1, listOf(cookie("refresh_token", "", 0)), false)
        assertEquals(1, store.records.size)
        val recreated = AuthCookieCustody(store, origin) { time }
        recreated.begin(2)
        recreated.restore(2)
        assertTrue(recreated.requestJar(2, true).loadForRequest(origin).isEmpty())
        assertEquals("/auth", store.record!!.path)
        assertTrue(recreated.requestJar(2, true).loadForRequest(origin.resolve("/refresh")!!).isEmpty())
        assertTrue(recreated.requestJar(2, true).loadForRequest(origin.resolve("/auth/refresh")!!).isEmpty())
    }

    @Test fun persistsOnlyRefreshAndRestoresOriginalMetadata() = runBlocking {
        custody.begin(1)
        assertTrue(custody.commit(1, pair(), true))
        assertEquals("refresh_token", store.record!!.name)
        val recreated = AuthCookieCustody(store, origin) { time }
        recreated.begin(2)
        assertEquals(RestoreCookies.AVAILABLE, recreated.restore(2))
        assertNull(recreated.access(2))
        assertEquals(pair()[1], recreated.requestJar(2, true).loadForRequest(origin.resolve("/refresh")!!).single())
    }

    @Test fun expiresAtExactBoundaryWithoutExtendingValidity() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        time = 1999
        assertNotNull(custody.access(1))
        time = 2000
        assertNull(custody.access(1))
        assertTrue(custody.requestJar(1, true).loadForRequest(origin).isEmpty())
        assertEquals(RestoreCookies.EXPIRED, custody.restore(1))
        assertNull(store.record)
    }

    @Test fun staleSuccessAndDeletionCannotMutateNewGenerationOrDisk() = runBlocking {
        custody.begin(1)
        val old = custody.requestJar(1, false)
        custody.begin(2)
        custody.commit(2, pair(), true)
        old.saveFromResponse(origin, listOf(cookie("refresh_token", "", 0)))
        assertFalse(custody.commit(1, old.received(), false))
        assertFalse(custody.commit(1, pair(), true))
        assertEquals("synthetic", store.record!!.value)
        assertNotNull(custody.access(2))
    }

    @Test fun deletionOnErrorIsAppliedAndMissingCookieIsPreserved() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        custody.commit(1, emptyList(), false)
        assertNotNull(store.record)
        custody.commit(1, listOf(cookie("refresh_token", "", 0)), false)
        assertNull(store.record)
    }

    @Test fun incompleteOrSessionCookiesRejectSuccess() = runBlocking {
        custody.begin(1)
        assertTrue(runCatching { custody.commit(1, listOf(cookie("access_token")), true) }.isFailure)
        val session = Cookie.Builder().name("refresh_token").value("synthetic")
            .hostOnlyDomain(origin.host).httpOnly().build()
        assertTrue(runCatching { custody.commit(1, listOf(cookie("access_token"), session), true) }.isFailure)
        assertNull(custody.access(1))
    }

    @Test fun writeFailureBlocksMemoryAndRevokesPersistedCredential() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        store.failWrite = true
        assertTrue(runCatching { custody.commit(1, pair(), true) }.isFailure)
        assertNull(custody.access(1))
        assertNull(store.record)
        assertTrue(store.revoked)
    }

    @Test fun jarDoesNotSendRefreshToLoginOtherPortOrInsecureOrigin() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        assertTrue(custody.requestJar(1, false).loadForRequest(origin).isEmpty())
        val jar = custody.requestJar(1, true)
        assertEquals(1, jar.loadForRequest(origin.resolve("/refresh")!!).size)
        assertTrue(jar.loadForRequest(origin.resolve("/auth/refresh")!!).isEmpty())
        assertTrue(jar.loadForRequest(origin.resolve("/login")!!).isEmpty())
        assertTrue(jar.loadForRequest("https://auth.example.test:444/refresh".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://auth.example.test/refresh".toHttpUrl()).isEmpty())
    }
}

class MemoryCookieStore : AuthCookieStore {
    var records = emptyList<RefreshCookieRecord>()
    val record get() = records.firstOrNull()
    var failWrite = false
    var revoked = false
    override suspend fun read() = records
    override suspend fun write(records: List<RefreshCookieRecord>) {
        check(!failWrite)
        this.records = records
    }
    override suspend fun revoke() {
        revoked = true
        records = emptyList()
    }
}
