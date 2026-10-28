package com.quistock.quistock.domain.port

enum class ProfileImageError { FORMAT, TOO_LARGE, PREPARATION, ACCESS }

sealed interface PreparedProfileImage {
    class Ready(val jpeg: ByteArray) : PreparedProfileImage
    class Failed(val reason: ProfileImageError) : PreparedProfileImage
}

interface ProfileImagePreparation {
    suspend fun prepare(source: ByteArray): PreparedProfileImage
}
