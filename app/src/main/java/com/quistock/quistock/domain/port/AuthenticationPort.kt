package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.LoginResult

interface AuthenticationPort {
    suspend fun authenticate(email: String, password: String): LoginResult
}
