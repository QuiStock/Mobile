package com.quistock.quistock.presentation.profile

import android.graphics.BitmapFactory
import androidx.core.view.isVisible
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentEditProfileBinding
import com.quistock.quistock.domain.model.ProfileNameError
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.profileNameError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EditProfileRenderer(
    private val binding: FragmentEditProfileBinding,
    private val uploadAvailable: Boolean,
    private val originalPhoto: suspend () -> ByteArray?,
) {
    private var displayed: ByteArray? = null

    suspend fun render(state: EditProfileState) {
        val editable = !state.busy && state.pending == null && state.original != null
        with(binding) {
            if (profileFullNameInput.text.toString() != state.name) profileFullNameInput.setText(state.name)
            profileFullNameInput.isEnabled = editable
            profileEmailInput.setText(state.original?.email.orEmpty())
            profileChangePhotoButton.isEnabled = editable
            profileCancelButton.isEnabled = !state.busy
            editProfileBackControl.isEnabled = !state.busy
            profileSaveChangesButton.isEnabled = canSubmit(state)
            profileSaveChangesButton.alpha = if (profileSaveChangesButton.isEnabled) 1f else DISABLED_ALPHA
            profileSaveChangesButton.setText(
                if (state.pending == null) R.string.profile_save_changes else R.string.profile_check_again,
            )
            profileFullNameInput.error = when (profileNameError(state.name)) {
                ProfileNameError.REQUIRED -> text(R.string.profile_name_required)
                ProfileNameError.TOO_LONG -> text(R.string.profile_name_too_long)
                null -> null
            }
            val message = status(state)
            profileEditStatus.isVisible = message != null
            profileEditStatus.text = message?.let(::text).orEmpty()
        }
        renderPhoto(state)
    }

    private fun canSubmit(state: EditProfileState): Boolean = when {
        state.busy -> false
        state.retryAt > profileEditNowMillis() -> false
        state.pending != null -> true
        else -> state.canSave
    }

    private fun status(state: EditProfileState): Int? = when (state.stage) {
        ProfileEditStage.PREPARING -> R.string.profile_preparing

        ProfileEditStage.UPLOADING -> R.string.profile_uploading

        ProfileEditStage.SAVING -> R.string.profile_saving

        ProfileEditStage.CHECKING -> R.string.profile_checking

        ProfileEditStage.IDLE -> error(state.message)
            ?: R.string.profile_upload_unavailable.takeUnless { uploadAvailable }
    }

    private fun error(message: ProfileEditMessage?): Int? = when (message) {
        ProfileEditMessage.UPLOAD -> R.string.profile_upload_failed
        ProfileEditMessage.SAVE -> R.string.profile_edit_failed
        ProfileEditMessage.INVALID -> R.string.profile_edit_invalid
        ProfileEditMessage.UNCERTAIN -> R.string.profile_edit_uncertain
        ProfileEditMessage.FORMAT -> R.string.profile_image_format
        ProfileEditMessage.TOO_LARGE -> R.string.profile_image_too_large
        ProfileEditMessage.PREPARATION -> R.string.profile_image_preparation
        ProfileEditMessage.ACCESS -> R.string.profile_permission
        null -> null
    }

    private suspend fun renderPhoto(state: EditProfileState) {
        val bytes = when {
            state.original == null || state.removePhoto -> null

            state.jpeg != null -> state.jpeg

            else -> try {
                originalPhoto()
            } catch (_: SessionException) {
                null
            }
        }
        if (bytes !== displayed || bytes == null) {
            val bitmap = bytes?.let { input ->
                withContext(Dispatchers.Default) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION) sample *= 2
                    BitmapFactory.decodeByteArray(
                        input,
                        0,
                        input.size,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                }
            }
            displayed = bytes
            if (bitmap == null) {
                binding.profileEditPhoto.setImageResource(R.drawable.ic_profile)
            } else {
                binding.profileEditPhoto.setImageBitmap(bitmap)
            }
        }
    }

    private fun text(id: Int): String = binding.root.context.getString(id)

    private companion object {
        const val DISABLED_ALPHA = 0.5f
        const val MAX_DIMENSION = 1024
    }
}
