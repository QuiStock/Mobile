package com.quistock.quistock.domain.port

interface AuthenticationPort {
    fun authenticate(
        email: String,
        password: String,
    ): Boolean
}
