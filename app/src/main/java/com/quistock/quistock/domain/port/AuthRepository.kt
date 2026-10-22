package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.LoginResult

interface AuthRepository {
    fun prepareLogout(generation: Long): LogoutRequest
    suspend fun begin(generation: Long)
    suspend fun login(email: String, password: String, generation: Long): LoginResult
    suspend fun refresh(generation: Long): AuthRefreshResult
    suspend fun restore(generation: Long): StoredSession
    suspend fun clear(generation: Long)
    fun retryDelayMillis(): Long = 0
    fun hasAccess(generation: Long): Boolean
}

/** Single-use remote operation; credential custody stays entirely inside data. */
fun interface LogoutRequest {
    suspend fun execute(): Boolean
}

enum class StoredSession { MISSING, EXPIRED, AVAILABLE }
