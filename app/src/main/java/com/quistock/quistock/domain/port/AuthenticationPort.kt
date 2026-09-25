package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.LegacyLoginResult

@Deprecated(
    message = "Login now uses authentication tokens instead of fetching the User directly. Use AuthRepository instead.",
    replaceWith = ReplaceWith("AuthRepository", "com.quistock.quistock.domain.port.AuthRepository"),
    level = DeprecationLevel.WARNING,
)
interface AuthenticationPort {
    suspend fun authenticate(email: String, password: String): LegacyLoginResult
}
