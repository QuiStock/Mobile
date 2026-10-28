package com.quistock.quistock.presentation.profile

import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.port.ProfilePhotoUploadResult
import com.quistock.quistock.domain.usecase.EditProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.EditProfileUseCase
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.coroutineContext

/** One owner (the ViewModel) serializes edits and submissions on the main dispatcher. */
internal class ProfileEditSubmission(
    private val editor: EditProfileUseCase,
    private val photos: EditProfilePhotoUseCase,
    private val expected: SessionSnapshot,
    private val state: MutableStateFlow<EditProfileState>,
) {
    suspend fun save() {
        val draft = state.value
        val jpeg = draft.jpeg
        if (jpeg != null && draft.uploadedUrl == null) {
            state.value = draft.copy(stage = ProfileEditStage.UPLOADING, message = null)
            val upload = photos.upload(jpeg)
            coroutineContext.ensureActive()
            if (upload is ProfilePhotoUploadResult.Failed) {
                state.value = draft.copy(
                    stage = ProfileEditStage.IDLE, message = ProfileEditMessage.UPLOAD,
                    retryAt = deadline(upload.retryAfterMillis),
                )
                return
            }
            state.value = state.value.copy(uploadedUrl = (upload as ProfilePhotoUploadResult.Uploaded).url)
        }
        val changes = state.value.changes()
        state.value = state.value.copy(stage = ProfileEditStage.SAVING, pending = changes, message = null)
        val result = editor.save(changes, expected)
        coroutineContext.ensureActive()
        if (result == ProfileEditResult.Uncertain) {
            confirm()
        } else {
            finish(result)
        }
    }

    suspend fun confirm() {
        val changes = requireNotNull(state.value.pending)
        state.value = state.value.copy(stage = ProfileEditStage.CHECKING, message = null)
        val result = editor.confirm(changes, expected)
        coroutineContext.ensureActive()
        finish(result)
    }

    private fun finish(result: ProfileEditResult) {
        val idle = state.value.copy(stage = ProfileEditStage.IDLE)
        state.value = when (result) {
            is ProfileEditResult.Saved -> EditProfileState(saved = true)
            is ProfileEditResult.Rejected -> idle.copy(
                pending = null,
                message = if (result.invalidInput) ProfileEditMessage.INVALID else ProfileEditMessage.SAVE,
                retryAt = deadline(result.retryAfterMillis),
            )
            ProfileEditResult.Uncertain -> idle.copy(message = ProfileEditMessage.UNCERTAIN)
            is ProfileEditResult.CheckLater -> idle.copy(
                message = ProfileEditMessage.UNCERTAIN,
                retryAt = deadline(result.retryAfterMillis),
            )
            ProfileEditResult.Forbidden -> EditProfileState()
        }
    }

    private fun deadline(delayMillis: Long): Long {
        val now = profileEditNowMillis()
        return now + delayMillis.coerceAtMost(Long.MAX_VALUE - now)
    }
}

internal fun profileEditNowMillis(): Long = System.nanoTime() / 1_000_000
