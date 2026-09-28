package com.quistock.quistock.data.secrets

import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.RefreshToken

object Secrets {
    val accessToken = SecretDefinition(
        type = AccessToken::class,
        key = "access_token",
        storageType = StorageType.MEMORY,
        serializer = AccessToken.serializer(),
    )

    val refreshToken = SecretDefinition(
        type = RefreshToken::class,
        key = "refresh_token",
        storageType = StorageType.PERSISTENT,
        serializer = RefreshToken.serializer(),
    )

    val all = listOf(accessToken, refreshToken)
}
