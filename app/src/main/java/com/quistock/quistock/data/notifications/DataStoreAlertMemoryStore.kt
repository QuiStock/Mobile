package com.quistock.quistock.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quistock.quistock.domain.model.AlertMemory
import com.quistock.quistock.domain.model.RoleAlertType
import com.quistock.quistock.domain.port.AlertMemoryStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.roleAlertsDataStore by preferencesDataStore(name = "quistock_role_alerts")

/** Per-user alert memory: preferences, delivered keys and owned request IDs (no product names). */
class DataStoreAlertMemoryStore internal constructor(private val store: DataStore<Preferences>) : AlertMemoryStore {
    constructor(context: Context) : this(context.applicationContext.roleAlertsDataStore)

    override suspend fun read(userId: String): AlertMemory = decode(store.data.first()[key(userId)])

    override suspend fun update(userId: String, transform: (AlertMemory) -> AlertMemory): AlertMemory {
        var result = AlertMemory()
        store.edit { preferences ->
            result = transform(decode(preferences[key(userId)]))
            preferences[key(userId)] = JSON.encodeToString(Stored.serializer(), Stored.from(result))
        }
        return result
    }

    private fun key(userId: String) = stringPreferencesKey("user:$userId")

    private fun decode(raw: String?): AlertMemory = try {
        raw?.let { JSON.decodeFromString(Stored.serializer(), it).toMemory() } ?: AlertMemory()
    } catch (_: SerializationException) {
        AlertMemory()
    } catch (_: IllegalArgumentException) {
        AlertMemory()
    }

    @Serializable
    private data class Stored(
        val disabled: List<String> = emptyList(),
        val delivered: List<String> = emptyList(),
        val own: List<String> = emptyList(),
    ) {
        fun toMemory() = AlertMemory(
            disabled = disabled.mapNotNull { name -> RoleAlertType.entries.find { it.name == name } }.toSet(),
            delivered = delivered.toSet(),
            ownRequests = own.toCollection(LinkedHashSet()),
        )

        companion object {
            fun from(memory: AlertMemory) = Stored(
                memory.disabled.map { it.name }.sorted(),
                memory.delivered.toList(),
                memory.ownRequests.toList(),
            )
        }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
