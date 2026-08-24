package com.quistock.quistock.domain.port

interface AuthenticationPort {
    suspend fun authenticate(email: String, password: String): Boolean
}
