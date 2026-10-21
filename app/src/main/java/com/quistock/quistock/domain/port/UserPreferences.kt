package com.quistock.quistock.domain.port

interface UserPreferences {
    suspend fun clearUserId()
    fun saveUserId(userId: String)
    fun getUserId(): String?
}
