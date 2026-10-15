package com.quistock.quistock.data.secrets

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quistock.quistock.data.remote.auth.AuthCookieStore
import com.quistock.quistock.data.remote.auth.RefreshCookieBatch
import com.quistock.quistock.data.remote.auth.RefreshCookieRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.KeyStore

private val Context.authCookieDataStore by preferencesDataStore(name = "auth_cookies")

class EncryptedAuthCookieStore(private val context: Context) : AuthCookieStore {
    private val dataStore = context.applicationContext.authCookieDataStore
    private val cipher = SecretCipher(ALIAS)
    private val recordKey = stringPreferencesKey("refresh_cookie_v1")

    override suspend fun read(): List<RefreshCookieRecord> = withContext(Dispatchers.IO) {
        // Values without HTTP expiry/scope are never interpreted as cookies.
        removeLegacyAuthCredential(context)
        val saved = dataStore.data.first()[recordKey] ?: return@withContext emptyList()
        val plaintext = cipher.decrypt(recordKey.name, Base64.decode(saved, Base64.NO_WRAP))
        val batch = Json.decodeFromString<RefreshCookieBatch>(plaintext.toString(Charsets.UTF_8))
        require(batch.version == 1) { "Unsupported credential version" }
        batch.records
    }

    override suspend fun write(records: List<RefreshCookieRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) {
            revoke()
        } else {
            val plaintext = Json.encodeToString(RefreshCookieBatch.serializer(), RefreshCookieBatch(records))
                .toByteArray(Charsets.UTF_8)
            val encrypted = Base64.encodeToString(cipher.encrypt(recordKey.name, plaintext), Base64.NO_WRAP)
            dataStore.edit { it[recordKey] = encrypted }
        }
        Unit
    }

    override suspend fun revoke() = withContext(Dispatchers.IO) {
        // Key destruction precedes disk removal: stale ciphertext cannot restore a revoked credential.
        revokeCredential(
            { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) },
            { dataStore.edit { it.remove(recordKey) } },
        )
        Unit
    }

    companion object {
        private const val ALIAS = "quistock_auth_cookies_v1"
    }
}
