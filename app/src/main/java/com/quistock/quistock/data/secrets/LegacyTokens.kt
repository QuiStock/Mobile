package com.quistock.quistock.data.secrets

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class RefreshToken(val token: String)

@JvmInline
@Serializable
value class AccessToken(val token: String)
