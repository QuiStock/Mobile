package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.model.RefreshToken

interface AuthRepository {
    suspend fun login(email: String, password: String): LoginResult
    suspend fun refresh(refreshToken: RefreshToken): LoginResult
}
