package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.SessionSnapshot

interface ProfileEditRepository {
    suspend fun update(changes: ProfileChanges, expected: SessionSnapshot): ProfileEditResult
}

interface ProfilePhotoUploadRepository {
    val available: Boolean
    suspend fun upload(jpeg: ByteArray): ProfilePhotoUploadResult
}

sealed interface ProfilePhotoUploadResult {
    class Uploaded(val url: String) : ProfilePhotoUploadResult
    class Failed(val retryAfterMillis: Long = 0) : ProfilePhotoUploadResult
}
