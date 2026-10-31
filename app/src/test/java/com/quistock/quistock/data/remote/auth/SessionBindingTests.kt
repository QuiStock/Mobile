package com.quistock.quistock.data.remote.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBindingTests {
    private val origin = "https://auth.example.test/".toHttpUrl()
    private val memory = ScopedMemoryStore()
    private val custody = AuthCookieCustody(memory, origin) { 1000L }
    private fun pair(expiry: Long = 2000L) = listOf("access_token", "refresh_token").map { name ->
        Cookie.Builder().name(name).value("synthetic").hostOnlyDomain(origin.host).path("/")
            .httpOnly().secure().expiresAt(expiry).build()
    }

    @Test fun bindingSurvivesRotationAndColdRestoreButNotAnotherLogin() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        assertNull(custody.binding(1))
        val bound = custody.bindProfile(1, "user", 51)!!
        assertTrue(bound.persisted)
        custody.commit(1, pair(3000), true)
        assertEquals(bound.sessionId, custody.binding(1)!!.sessionId)
        assertEquals(3000L, custody.binding(1)!!.expiresAt)
        val reopened = AuthCookieCustody(memory, origin) { 1000L }
        reopened.begin(5)
        assertEquals(RestoreCookies.AVAILABLE, reopened.restore(5))
        assertEquals(bound.sessionId, reopened.binding(5)!!.sessionId)
        assertNull(reopened.access(5))
        reopened.clear(5)
        reopened.begin(6)
        reopened.commit(6, pair(), true)
        assertNull(reopened.binding(6))
        assertNotEquals(bound.sessionId, reopened.bindProfile(6, "user", 51)!!.sessionId)
    }

    @Test fun failedStoreReplacementRevokesOldDurableIdentityButRetainsOnlineCredentials() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        custody.bindProfile(1, "user", 51)
        memory.failWrites = true
        val current = custody.bindProfile(1, "user", 52)!!
        assertEquals(52L, current.storeId)
        assertFalse(current.persisted)
        assertTrue(memory.batch.records.isEmpty())
        assertEquals("synthetic", custody.access(1))
    }

    @Test fun legacyCredentialsCannotSelectAnOfflineIdentityAndExpiryIsEnforced() = runBlocking {
        memory.batch =
            RefreshCookieBatch(pair().filter { it.name == "refresh_token" }.map(RefreshCookieRecord::from), version = 1)
        custody.begin(1)
        assertEquals(RestoreCookies.AVAILABLE, custody.restore(1))
        assertNull(custody.binding(1))
        val expired = AuthCookieCustody(memory, origin) { 3000L }
        expired.begin(2)
        assertEquals(RestoreCookies.EXPIRED, expired.restore(2))
        assertNull(expired.binding(2))
    }

    @Test fun oldGenerationCannotReplaceIdentityOrRestoreRevokedCredentials() = runBlocking {
        custody.begin(1)
        custody.commit(1, pair(), true)
        custody.bindProfile(1, "user", 51)
        custody.begin(2)
        custody.clear(2)
        assertNull(custody.bindProfile(1, "other", 52))
        assertFalse(custody.commit(1, pair(), true))
        assertTrue(memory.batch.records.isEmpty())
    }
}

private class ScopedMemoryStore : AuthCookieStore {
    var batch = RefreshCookieBatch(emptyList())
    var failWrites = false
    override suspend fun read() = batch.records
    override suspend fun write(records: List<RefreshCookieRecord>) {
        batch = RefreshCookieBatch(records)
    }
    override suspend fun readSession() = batch
    override suspend fun writeSession(session: RefreshCookieBatch) {
        if (failWrites) throw java.io.IOException("Storage unavailable")
        batch = session
    }
    override suspend fun revoke() {
        batch = RefreshCookieBatch(emptyList())
    }
}
