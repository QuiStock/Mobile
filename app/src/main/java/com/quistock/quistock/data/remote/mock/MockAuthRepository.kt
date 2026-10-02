package com.quistock.quistock.data.remote.mock

import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.AuthTokens
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.model.RefreshToken
import com.quistock.quistock.domain.port.AuthRepository

/** Client-side fixture only; no API route or production credential contract. */
class MockAuthRepository : AuthRepository {
    override suspend fun login(email: String, password: String): LoginResult =
        if (email.isBlank() || password.isBlank()) LoginError.InvalidCredentials else success()

    override suspend fun refresh(refreshToken: RefreshToken): AuthRefreshResult =
        if (refreshToken.token.isBlank()) AuthRefreshError.InvalidToken else success()

    private fun success() = AuthResult.Success(
        AuthTokens(AccessToken("synthetic-access"), RefreshToken("synthetic-opaque-refresh")),
    )
}
