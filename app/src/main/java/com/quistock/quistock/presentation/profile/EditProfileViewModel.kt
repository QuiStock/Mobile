package com.quistock.quistock.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.PreparedProfileImage
import com.quistock.quistock.domain.port.ProfileImageError
import com.quistock.quistock.domain.usecase.EditProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.EditProfileUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class EditProfileViewModel(
    editor: EditProfileUseCase,
    private val photos: EditProfilePhotoUseCase,
    profile: ProfileUseCase,
    private val session: SessionUseCase,
) : ViewModel() {
    private val expected = session.snapshot()
    private val initial = (profile.state.value as? ProfileState.Ready)
        ?.takeIf { it.generation == expected.generation }?.profile
    private val mutableState = MutableStateFlow(EditProfileState(initial))
    val state = mutableState.asStateFlow()
    val uploadAvailable: Boolean get() = photos.available
    private val submission = ProfileEditSubmission(editor, photos, expected, mutableState)
    private var operation: Job? = null
    var cameraPath: String? = null

    init {
        viewModelScope.launch {
            session.state.collect { status ->
                if (status != SessionState.Active || session.snapshot().generation != expected.generation) {
                    operation?.cancel()
                    clearCamera()
                    mutableState.value = EditProfileState()
                }
            }
        }
    }

    fun rename(value: String) {
        val state = state.value
        if (!state.busy && state.pending == null) mutableState.value = state.copy(name = value, message = null)
    }

    fun removePhoto() {
        val state = state.value
        if (!state.busy && state.pending == null) {
            mutableState.value = state.copy(
                photo = null, uploadedUrl = null, removePhoto = state.original?.photoUrl != null, message = null,
            )
        }
    }

    fun prepare(read: suspend () -> ByteArray?) {
        if (!mediaAllowed()) return
        mutableState.value = state.value.copy(stage = ProfileEditStage.PREPARING, message = null)
        operation = viewModelScope.launch {
            val bytes = try {
                read()
            } catch (_: SecurityException) {
                mediaError(ProfileImageError.ACCESS)
                return@launch
            }
            val image = bytes?.let { photos.prepare(it) }
                ?: PreparedProfileImage.Failed(ProfileImageError.PREPARATION)
            coroutineContext.ensureActive()
            when (image) {
                is PreparedProfileImage.Ready -> mutableState.value = state.value.copy(
                    stage = ProfileEditStage.IDLE, photo = ProfileEditPhoto(image.jpeg),
                    uploadedUrl = null, removePhoto = false,
                )
                is PreparedProfileImage.Failed -> mediaError(image.reason)
            }
        }
    }

    fun mediaError(error: ProfileImageError = ProfileImageError.PREPARATION) {
        val message = when (error) {
            ProfileImageError.FORMAT -> ProfileEditMessage.FORMAT
            ProfileImageError.TOO_LARGE -> ProfileEditMessage.TOO_LARGE
            ProfileImageError.PREPARATION -> ProfileEditMessage.PREPARATION
            ProfileImageError.ACCESS -> ProfileEditMessage.ACCESS
        }
        mutableState.value = state.value.copy(stage = ProfileEditStage.IDLE, message = message)
    }

    private fun mediaAllowed(): Boolean =
        !state.value.busy && state.value.pending == null && state.value.original != null

    fun save() {
        val draft = state.value
        if (!submissionAllowed(draft)) return
        mutableState.value = draft.copy(stage = ProfileEditStage.SAVING, message = null)
        operation = viewModelScope.launch {
            try {
                session.requireAccess(expected)
                if (draft.pending != null) submission.confirm() else submission.save()
            } catch (_: SessionException) {
                mutableState.value = state.value.copy(stage = ProfileEditStage.IDLE)
            }
        }
    }

    private fun submissionAllowed(draft: EditProfileState): Boolean = when {
        draft.busy || draft.original == null -> false
        profileEditNowMillis() < draft.retryAt -> false
        else -> draft.canSave || draft.pending != null
    }

    fun discard() {
        operation?.cancel()
        clearCamera()
        mutableState.value = EditProfileState()
    }

    fun clearCamera() {
        cameraPath?.let { java.io.File(it).delete() }
        cameraPath = null
    }

    override fun onCleared() {
        clearCamera()
        super.onCleared()
    }
}
