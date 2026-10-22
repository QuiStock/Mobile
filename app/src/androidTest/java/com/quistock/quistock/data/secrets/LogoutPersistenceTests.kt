package com.quistock.quistock.data.secrets

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.quistock.quistock.R
import com.quistock.quistock.data.preferences.AndroidUserPreferences
import com.quistock.quistock.data.remote.auth.RefreshCookieRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoutPersistenceTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun revocationRemovesCurrentAndLegacyRefreshAcrossStoreRecreation() = runBlocking {
        val store = EncryptedAuthCookieStore(context)
        val legacyKey = stringPreferencesKey("refresh_token")
        try {
            store.write(
                listOf(
                    RefreshCookieRecord.from(
                        Cookie.Builder().name("refresh_token").value("synthetic-refresh")
                            .hostOnlyDomain("auth.example.test").path("/").httpOnly().secure()
                            .expiresAt(System.currentTimeMillis() + 60000).build(),
                    ),
                ),
            )
            context.legacyAuthDataStore.edit { it[legacyKey] = "synthetic-legacy" }
            store.revoke()
            assertTrue(EncryptedAuthCookieStore(context).read().isEmpty())
            assertNull(context.legacyAuthDataStore.data.first()[legacyKey])
        } finally {
            store.revoke()
        }
    }

    @Test fun identityRemovalPreservesUnrelatedPreferences() = runBlocking {
        val preferences = AndroidUserPreferences(context)
        val backing = context.getSharedPreferences(
            context.getString(R.string.shared_preferences_name),
            Context.MODE_PRIVATE,
        )
        val unrelated = "logout_test_device_preference"
        try {
            preferences.saveUserId("synthetic-user")
            assertTrue(backing.edit().putString(unrelated, "preserved").commit())
            preferences.clearUserId()
            assertNull(AndroidUserPreferences(context).getUserId())
            assertEquals("preserved", backing.getString(unrelated, null))
        } finally {
            backing.edit().remove(unrelated).commit()
            preferences.clearUserId()
        }
    }
}
