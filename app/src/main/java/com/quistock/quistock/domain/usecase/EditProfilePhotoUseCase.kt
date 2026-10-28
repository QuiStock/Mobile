package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.port.ProfileImagePreparation
import com.quistock.quistock.domain.port.ProfilePhotoUploadRepository

class EditProfilePhotoUseCase(
    private val preparation: ProfileImagePreparation,
    private val repository: ProfilePhotoUploadRepository,
) {
    val available: Boolean get() = repository.available
    suspend fun prepare(bytes: ByteArray) = preparation.prepare(bytes)
    suspend fun upload(jpeg: ByteArray) = repository.upload(jpeg)
}
