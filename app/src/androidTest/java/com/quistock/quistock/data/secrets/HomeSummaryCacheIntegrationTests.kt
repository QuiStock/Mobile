package com.quistock.quistock.data.secrets

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.quistock.quistock.data.preferences.DataStoreHomeSummaryCache
import com.quistock.quistock.data.remote.auth.AuthCookieCustody
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
    private val cache = DataStoreHomeSummaryCache(context)
    private val custody = AuthCookieCustody(store, "https://auth.example.test/".toHttpUrl())
    private val summary = CachedHomeSummary(HomeSummary(4, 2, 3, 5), System.currentTimeMillis())
    private fun cookies() = listOf("access_token", "refresh_token").map {
        Cookie.Builder().name(it).value("synthetic").hostOnlyDomain("auth.example.test").path("/")
            .secure().httpOnly().expiresAt(System.currentTimeMillis() + 60_000).build()
    }

    @Test fun realEncryptedDataStoreSurvivesRecreationAndRejectsOtherScopes() = runBlocking {
        try {
            store.revoke()
            custody.begin(1)
            custody.commit(1, cookies(), true)
            val scope = custody.bindProfile(1, "fixture", 51)!!
            assertTrue(cache.write(scope, summary))
            assertEquals(summary, DataStoreHomeSummaryCache(context).read(scope))
            assertNull(cache.read(scope.copy(userId = "other")))
            assertNull(cache.read(scope.copy(storeId = 52)))
            custody.commit(1, cookies(), true)
            assertEquals(summary, cache.read(custody.binding(1)!!))
            custody.bindProfile(1, "fixture", 52)
            assertNull(cache.read(scope))
            assertNull(cache.read(custody.binding(1)!!))
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
            context.authCookieDataStore.edit { it[homeSummaryKey] = "corrupt" }
            assertNull(cache.read(scope))
            assertTrue(store.read().isNotEmpty())
            assertTrue(cache.write(scope, summary))
            assertEquals(summary, cache.read(scope))
        } finally {
            store.revoke()
        }
    }
}
