package com.quistock.quistock.domain.port

interface UserPreferences {
    fun saveUserId(userId: String)
    fun getUserId(): String?
}
