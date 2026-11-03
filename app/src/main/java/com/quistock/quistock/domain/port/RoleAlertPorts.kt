package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.AlertMemory
import com.quistock.quistock.domain.model.RoleAlert

/** Device-local alert memory per Core user ID. Updates are atomic per call. */
interface AlertMemoryStore {
    suspend fun read(userId: String): AlertMemory
    suspend fun update(userId: String, transform: (AlertMemory) -> AlertMemory): AlertMemory
}

/** Posts and removes device notifications. */
interface AlertNotifier {
    fun canNotify(): Boolean
    fun post(alerts: List<RoleAlert>)
    fun clearAll()
}

/** Schedules the periodic background check. */
interface AlertScheduler {
    fun start()
    fun stop()
}

/** Records requests the current user forwarded to the manager from this device. */
fun interface RequestOwnership {
    suspend fun remember(userId: String, actionId: String)
}
