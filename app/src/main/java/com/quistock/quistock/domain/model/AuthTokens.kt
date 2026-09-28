package com.quistock.quistock.domain.model

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class RefreshToken(val token: String)

@JvmInline
@Serializable
value class AccessToken(val token: String)

data class AuthTokens(val accessToken: AccessToken, val refreshToken: RefreshToken)
