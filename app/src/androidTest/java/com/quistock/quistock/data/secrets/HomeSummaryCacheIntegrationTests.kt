package com.quistock.quistock.data.secrets

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.quistock.quistock.data.preferences.SqliteHomeSummaryCache
import com.quistock.quistock.data.remote.auth.AuthCookieCustody
import com.quistock.quistock.data.remote.auth.RefreshCookieBatch
import com.quistock.quistock.data.remote.auth.RefreshCookieRecord
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSummaryCacheIntegrationTests {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = EncryptedAuthCookieStore(context)
    private val cache = SqliteHomeSummaryCache(context)
    private val custody = AuthCookieCustody(store, "https://auth.example.test/".toHttpUrl())
    private val summary = CachedHomeSummary(HomeSummary(4, 2, 3, 5), System.currentTimeMillis())
    private fun cookies() = listOf("access_token", "refresh_token").map {
        Cookie.Builder().name(it).value("synthetic").hostOnlyDomain("auth.example.test").path("/")
            .secure().httpOnly().expiresAt(System.currentTimeMillis() + 60_000).build()
    }

    @Test fun encryptedSqliteSurvivesRecreationAndRejectsOtherScopes() = runBlocking {
        try {
            store.revoke()
            custody.begin(1)
            custody.commit(1, cookies(), true)
            val scope = custody.bindProfile(1, "fixture", 51)!!
            assertTrue(cache.write(scope, summary))
            assertEquals(summary, SqliteHomeSummaryCache(context).read(scope))
            assertNull(cache.read(scope.copy(userId = "other")))
            assertNull(cache.read(scope.copy(storeId = 52)))
            custody.begin(2)
            custody.commit(2, cookies(), true)
            val replacement = custody.bindProfile(2, "other", 52)!!
            assertNull(cache.read(scope))
            assertNull(cache.read(replacement))
        } finally {
            store.revoke()
        }
    }

    @Test fun logoutAndQueuedSqliteWriteShareTheAuthLockAcrossInstances() = runBlocking {
        try {
            custody.begin(1)
            custody.commit(1, cookies(), true)
            val scope = custody.bindProfile(1, "fixture", 51)!!
            val (logout, write) = authPersistenceMutex.withLock {
                async(start = CoroutineStart.UNDISPATCHED) { EncryptedAuthCookieStore(context).revoke() } to
                    async(start = CoroutineStart.UNDISPATCHED) { SqliteHomeSummaryCache(context).write(scope, summary) }
            }

            logout.await()
            assertFalse(write.await())
            assertNull(SqliteHomeSummaryCache(context).read(scope))
            assertTrue(EncryptedAuthCookieStore(context).read().isEmpty())
        } finally {
            store.revoke()
        }
    }

    @Test fun accountReplacementAndQueuedSqliteWriteShareTheAuthLockAcrossInstances() = runBlocking {
        try {
            custody.begin(1)
            custody.commit(1, cookies(), true)
            val scope = custody.bindProfile(1, "fixture", 51)!!
            val replacement = RefreshCookieBatch(
                cookies().filter { it.name == "refresh_token" }.map(RefreshCookieRecord::from),
                sessionId = "replacement-session",
                userId = "other-user",
                storeId = 52,
            )
            val (writeSession, writeCache) = authPersistenceMutex.withLock {
                async(start = CoroutineStart.UNDISPATCHED) {
                    EncryptedAuthCookieStore(context).writeSession(replacement)
                } to async(start = CoroutineStart.UNDISPATCHED) {
                    SqliteHomeSummaryCache(context).write(scope, summary)
                }
            }

            writeSession.await()
            assertFalse(writeCache.await())
            assertNull(SqliteHomeSummaryCache(context).read(scope))
        } finally {
            store.revoke()
        }
    }

    @Test fun aQueuedWriteCannotRepopulateCacheAfterLogout() = runBlocking {
        try {
            custody.begin(1)
            custody.commit(1, cookies(), true)
            val scope = custody.bindProfile(1, "fixture", 51)!!
            val release = CompletableDeferred<Unit>()
            val pending = async {
                release.await()
                cache.write(scope, summary)
            }
            custody.begin(2)
            custody.clear(2)
            release.complete(Unit)
            assertFalse(pending.await())
            assertNull(cache.read(scope))
            assertTrue(store.read().isEmpty())
        } finally {
            store.revoke()
        }
    }

    @Test fun corruptCacheCannotGrantOfflineAccessOrDestroyTheCredential() = runBlocking {
        try {
            custody.begin(1)
            custody.commit(1, cookies(), true)
            val scope = custody.bindProfile(1, "fixture", 51)!!
            assertTrue(cache.write(scope, summary))
            // A damaged encrypted SQLite row cannot grant offline access or destroy credentials.
            context.openOrCreateDatabase("home_summary.db", android.content.Context.MODE_PRIVATE, null)
                .use { db ->
                    db.execSQL("UPDATE home_summary SET payload = ?", arrayOf(byteArrayOf(1, 2, 3)))
                }
            assertNull(cache.read(scope))
            assertTrue(store.read().isNotEmpty())
            assertTrue(cache.write(scope, summary))
            assertEquals(summary, cache.read(scope))
        } finally {
            store.revoke()
        }
    }

    @Test fun sqliteContainsOnlyEncryptedSummaryAndMigratesLegacyDatastoreValue() = runBlocking {
        try {
            custody.begin(1)
            custody.commit(1, cookies(), true)
            val scope = custody.bindProfile(1, "fixture", 51)!!
            cache.write(scope, summary)

            val dbFile = context.getDatabasePath("home_summary.db")
            val dbBytes = dbFile.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(dbBytes.contains("fixture"))
            assertFalse(dbBytes.contains("\"near\":4"))
            assertEquals(summary, SqliteHomeSummaryCache(context).read(scope))
        } finally {
            store.revoke()
        }
    }

    @Test fun firstSqliteAccessDiscardsLegacyDatastoreSummary() = runBlocking {
        try {
            custody.begin(1)
            custody.commit(1, cookies(), true)
            val scope = custody.bindProfile(1, "fixture", 51)!!
            val legacyKey = stringPreferencesKey("home_summary_v1")
            context.authCookieDataStore.edit { it[legacyKey] = "legacy-ciphertext" }

            assertTrue(cache.write(scope, summary))
            assertNull(context.authCookieDataStore.data.first()[legacyKey])
        } finally {
            store.revoke()
        }
    }
}
