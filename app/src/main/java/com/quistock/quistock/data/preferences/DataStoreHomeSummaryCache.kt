package com.quistock.quistock.data.preferences

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import com.quistock.quistock.data.remote.auth.RefreshCookieBatch
import com.quistock.quistock.data.secrets.AUTH_KEY_ALIAS
import com.quistock.quistock.data.secrets.SecretCipher
import com.quistock.quistock.data.secrets.authCookieDataStore
import com.quistock.quistock.data.secrets.authRecordKey
import com.quistock.quistock.data.secrets.decodeAuthRecord
import com.quistock.quistock.data.secrets.homeSummaryKey
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummary
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.port.HomeSummaryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DataStoreHomeSummaryCache(context: Context) : HomeSummaryCache {
    private val store = context.applicationContext.authCookieDataStore
    private val cipher = SecretCipher(AUTH_KEY_ALIAS)

    override suspend fun read(scope: SessionBinding): CachedHomeSummary? = withContext(Dispatchers.IO) {
        val values = store.data.first()
        val record = values[authRecordKey]?.let { runCatching { decodeAuthRecord(it) }.getOrNull() }
        if (!matches(record, scope)) return@withContext null
        values[homeSummaryKey]?.let { saved ->
            runCatching {
                val bytes = cipher.decrypt(homeSummaryKey.name, Base64.decode(saved, Base64.NO_WRAP))
                val cached = Json.decodeFromString<SummaryRecord>(bytes.toString(Charsets.UTF_8))
                require(
                    cached.version == 1 && cached.sessionId == scope.sessionId &&
                        cached.userId == scope.userId && cached.storeId == scope.storeId && cached.receivedAt > 0,
                )
                CachedHomeSummary(
                    HomeSummary(cached.near, cached.stockout, cached.overstock, cached.actions),
                    cached.receivedAt,
                )
            }.getOrNull()
        }
    }

    override suspend fun write(scope: SessionBinding, summary: CachedHomeSummary): Boolean =
        withContext(Dispatchers.IO) {
            val record = SummaryRecord(
                scope.sessionId,
                scope.userId,
                scope.storeId,
                summary.summary.nearExpiry,
                summary.summary.stockout,
                summary.summary.overstock,
                summary.summary.activeActions,
                summary.receivedAt,
            )
            val bytes = Json.encodeToString(SummaryRecord.serializer(), record).toByteArray(Charsets.UTF_8)
            val encrypted = Base64.encodeToString(cipher.encrypt(homeSummaryKey.name, bytes), Base64.NO_WRAP)
            var accepted = false
            // Credential replacement/revocation and cache writes share this atomic DataStore transaction.
            store.edit { values ->
                val session = values[authRecordKey]?.let { runCatching { decodeAuthRecord(it) }.getOrNull() }
                if (matches(session, scope)) {
                    values[homeSummaryKey] = encrypted
                    accepted = true
                }
            }
            accepted
        }

    private fun matches(session: RefreshCookieBatch?, scope: SessionBinding): Boolean =
        scope.persisted && scope.validAt(System.currentTimeMillis()) && session?.sessionId == scope.sessionId &&
            session.userId == scope.userId && session.storeId == scope.storeId &&
            session.records.any { it.name == "refresh_token" && it.expiresAt >= scope.expiresAt }
}

@Serializable
private class SummaryRecord(
    val sessionId: String,
    val userId: String,
    val storeId: Long,
    val near: Long,
    val stockout: Long,
    val overstock: Long,
    val actions: Long,
    val receivedAt: Long,
    val version: Int = 1,
)
