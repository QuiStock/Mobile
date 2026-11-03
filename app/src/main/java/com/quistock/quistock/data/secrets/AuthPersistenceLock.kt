package com.quistock.quistock.data.secrets

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val authPersistenceMutex = Mutex()

internal suspend fun <T> withAuthPersistenceLock(block: suspend () -> T): T = authPersistenceMutex.withLock { block() }
