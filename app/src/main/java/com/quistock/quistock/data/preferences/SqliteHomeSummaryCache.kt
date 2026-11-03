package com.quistock.quistock.data.preferences

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.datastore.preferences.core.edit
import com.quistock.quistock.data.remote.auth.RefreshCookieBatch
import com.quistock.quistock.data.secrets.AUTH_KEY_ALIAS
import com.quistock.quistock.data.secrets.SecretCipher
import com.quistock.quistock.data.secrets.authCookieDataStore
import com.quistock.quistock.data.secrets.authPersistenceMutex
import com.quistock.quistock.data.secrets.authRecordKey
import com.quistock.quistock.data.secrets.decodeAuthRecord
import com.quistock.quistock.data.secrets.homeSummaryKey
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummary
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.port.HomeSummaryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SqliteHomeSummaryCache(context: Context) : HomeSummaryCache {
    private val store = context.applicationContext.authCookieDataStore
    private val database = SummaryDatabase(context.applicationContext)
    private val cipher = SecretCipher(AUTH_KEY_ALIAS)

    override suspend fun read(scope: SessionBinding): CachedHomeSummary? = authPersistenceMutex.withLock {
        withContext(Dispatchers.IO) {
            discardLegacyCache()
            val credential = credential()
            if (!matches(credential, scope)) return@withContext null
            val payload = runCatching { database.read() }.getOrNull() ?: return@withContext null
            val cached = runCatching {
                val bytes = cipher.decrypt(CACHE_CIPHER_KEY, payload)
                val record = Json.decodeFromString<SummaryRecord>(bytes.toString(Charsets.UTF_8))
                require(
                    record.version == VERSION && record.sessionId == scope.sessionId && record.userId == scope.userId &&
                        record.storeId == scope.storeId && record.receivedAt > 0,
                )
                CachedHomeSummary(
                    HomeSummary(record.near, record.stockout, record.overstock, record.actions),
                    record.receivedAt,
                )
            }.getOrNull()
            if (cached == null) runCatching { database.clear() }
            cached
        }
    }

    override suspend fun write(scope: SessionBinding, summary: CachedHomeSummary): Boolean =
        authPersistenceMutex.withLock {
            withContext(Dispatchers.IO) {
                discardLegacyCache()
                if (!matches(credential(), scope)) return@withContext false
                require(summary.receivedAt > 0)
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
                database.write(cipher.encrypt(CACHE_CIPHER_KEY, bytes))
                true
            }
        }

    private suspend fun credential(): RefreshCookieBatch? =
        store.data.first()[authRecordKey]?.let { runCatching { decodeAuthRecord(it) }.getOrNull() }

    private suspend fun discardLegacyCache() {
        store.edit { it.remove(homeSummaryKey) }
    }

    private fun matches(session: RefreshCookieBatch?, scope: SessionBinding): Boolean =
        scope.persisted && scope.validAt(System.currentTimeMillis()) && session?.sessionId == scope.sessionId &&
            session.userId == scope.userId && session.storeId == scope.storeId &&
            session.records.any { it.name == "refresh_token" && it.expiresAt >= scope.expiresAt }

    private class SummaryDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $TABLE_NAME (id INTEGER PRIMARY KEY CHECK(id = 1), payload BLOB NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }

        fun read(): ByteArray? = readableDatabase.rawQuery("SELECT payload FROM $TABLE_NAME WHERE id = 1", null).use {
            if (it.moveToFirst()) it.getBlob(0) else null
        }

        fun write(payload: ByteArray) {
            val values = ContentValues(2).apply {
                put("id", 1)
                put("payload", payload)
            }
            writableDatabase.beginTransaction()
            try {
                check(
                    writableDatabase.insertWithOnConflict(
                        TABLE_NAME,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    ) == 1L,
                ) { "Could not persist home summary" }
                writableDatabase.setTransactionSuccessful()
            } finally {
                writableDatabase.endTransaction()
            }
        }

        fun clear() {
            writableDatabase.delete(TABLE_NAME, null, null)
        }
    }

    private companion object {
        const val DATABASE_NAME = "home_summary.db"
        const val TABLE_NAME = "home_summary"
        const val CACHE_CIPHER_KEY = "home_summary_v2"
        const val VERSION = 2
    }
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
    val version: Int = 2,
)
