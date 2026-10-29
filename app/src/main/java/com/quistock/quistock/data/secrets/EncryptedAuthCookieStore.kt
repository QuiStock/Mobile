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

internal val Context.authCookieDataStore by preferencesDataStore(name = "auth_cookies")

class EncryptedAuthCookieStore(private val context: Context) : AuthCookieStore {
    private val dataStore = context.applicationContext.authCookieDataStore
    private val cipher = SecretCipher(ALIAS)
    private val recordKey = authRecordKey

    override suspend fun read(): List<RefreshCookieRecord> = readSession().records

    override suspend fun readSession(): RefreshCookieBatch = withContext(Dispatchers.IO) {
        removeLegacyAuthCredential(context)
        val saved = dataStore.data.first()[recordKey] ?: return@withContext RefreshCookieBatch(emptyList())
        decodeAuthRecord(saved)
    }

    override suspend fun write(records: List<RefreshCookieRecord>) = writeSession(RefreshCookieBatch(records))

    override suspend fun writeSession(session: RefreshCookieBatch) = withContext(Dispatchers.IO) {
        if (session.records.isEmpty()) {
            revoke()
        } else {
            val plaintext = Json.encodeToString(RefreshCookieBatch.serializer(), session).toByteArray(Charsets.UTF_8)
            val encrypted = Base64.encodeToString(cipher.encrypt(recordKey.name, plaintext), Base64.NO_WRAP)
            dataStore.edit { saved ->
                val previous = saved[recordKey]?.let { runCatching { decodeAuthRecord(it) }.getOrNull() }
                if (previous?.sessionId != session.sessionId || previous?.userId != session.userId ||
                    previous?.storeId != session.storeId
                ) {
                    saved.remove(homeSummaryKey)
                }
                saved[recordKey] = encrypted
            }
        }
        Unit
    }

    override suspend fun revoke() = withContext(Dispatchers.IO) {
        // Key destruction precedes disk removal: stale ciphertext cannot restore a revoked credential.
        val current = runCatching {
            revokeCredential(
                { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) },
                {
                    dataStore.edit {
                        it.remove(recordKey)
                        it.remove(homeSummaryKey)
                    }
                },
            )
        }
        val legacy = runCatching { removeLegacyAuthCredential(context) }
        current.getOrThrow()
        legacy.getOrThrow()
        Unit
    }

    companion object {
        private const val ALIAS = AUTH_KEY_ALIAS
    }
}

internal const val AUTH_KEY_ALIAS = "quistock_auth_cookies_v1"
internal val authRecordKey = stringPreferencesKey("refresh_cookie_v1")
internal val homeSummaryKey = stringPreferencesKey("home_summary_v1")

internal fun decodeAuthRecord(saved: String): RefreshCookieBatch {
    val bytes = SecretCipher(AUTH_KEY_ALIAS).decrypt(authRecordKey.name, Base64.decode(saved, Base64.NO_WRAP))
    val batch = Json.decodeFromString<RefreshCookieBatch>(bytes.toString(Charsets.UTF_8))
    require(batch.version in 1..2) { "Unsupported credential version" }
    return batch
}
