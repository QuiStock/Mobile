package com.quistock.quistock.domain.model

import kotlin.time.Instant

@SecretKey("refresh_token")
data class RefreshToken(val token: String, override val expiresAt: Instant) : SecretValue(expiresAt = expiresAt)

@SecretKey("access_token")
data class AccessToken(val token: String, override val expiresAt: Instant) : SecretValue(expiresAt = expiresAt)

data class AuthTokens(val accessToken: AccessToken, val refreshToken: RefreshToken)
