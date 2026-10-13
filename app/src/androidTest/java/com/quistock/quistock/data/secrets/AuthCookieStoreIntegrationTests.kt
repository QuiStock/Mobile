package com.quistock.quistock.data.secrets

import androidx.test.core.app.ApplicationProvider
import com.quistock.quistock.data.remote.auth.RefreshCookieRecord
import com.quistock.quistock.domain.model.RefreshToken
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStore

class AuthCookieStoreIntegrationTests {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun record() = RefreshCookieRecord.from(
        Cookie.Builder().name("refresh_token")
            .value("synthetic-refresh-only").hostOnlyDomain("auth.example.test").path("/auth")
            .expiresAt(System.currentTimeMillis() + 60000).secure().httpOnly().build(),
    )

    @Test fun encryptedRecordSurvivesRecreationWithMetadataAndNoPlaintext() = runBlocking {
        val store = EncryptedAuthCookieStore(context)
        try {
            val original = record()
            store.write(listOf(original))
            assertEquals(original.cookie(), EncryptedAuthCookieStore(context).read().single().cookie())
            val bytes = java.io.File(context.filesDir, "datastore/auth_cookies.preferences_pb").readBytes()
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(original.value))
        } finally {
            store.revoke()
        }
    }

    @Test fun destroyedKeyPreventsRestorationEvenWithCiphertextStillOnDisk() = runBlocking {
        val store = EncryptedAuthCookieStore(context)
        try {
            store.write(listOf(record()))
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("quistock_auth_cookies_v1")
            assertTrue(runCatching { EncryptedAuthCookieStore(context).read() }.isFailure)
        } finally {
            store.revoke()
        }
    }

    @Test fun upgradeDeletesLegacyValueWithoutCreatingCookieValidity() = runBlocking {
        val legacy = SecretStorageImpl(context)
        val store = EncryptedAuthCookieStore(context)
        store.revoke()
        legacy.save(RefreshToken("synthetic-legacy"), RefreshToken::class)
        assertTrue(store.read().isEmpty())
        assertNull(legacy.read(RefreshToken::class))
    }
}
