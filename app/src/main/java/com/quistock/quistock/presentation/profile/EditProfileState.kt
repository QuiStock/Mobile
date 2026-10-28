package com.quistock.quistock.presentation.profile

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.profileNameError

enum class ProfileEditStage { IDLE, PREPARING, UPLOADING, SAVING, CHECKING }
enum class ProfileEditMessage { UPLOAD, SAVE, INVALID, UNCERTAIN, FORMAT, TOO_LARGE, PREPARATION, ACCESS }

class ProfileEditPhoto(val jpeg: ByteArray)

data class EditProfileState(
    val original: UserProfile? = null,
    val name: String = original?.name.orEmpty(),
    val photo: ProfileEditPhoto? = null,
    val removePhoto: Boolean = false,
    val uploadedUrl: String? = null,
    val pending: ProfileChanges? = null,
    val stage: ProfileEditStage = ProfileEditStage.IDLE,
    val message: ProfileEditMessage? = null,
    val retryAt: Long = 0,
    val saved: Boolean = false,
) {
    val jpeg: ByteArray? get() = photo?.jpeg
    val busy: Boolean get() = stage != ProfileEditStage.IDLE
    val dirty: Boolean get() = name != original?.name || jpeg != null || removePhoto
    val canSave: Boolean get() = original != null && dirty && profileNameError(name) == null

    fun changes(): ProfileChanges = ProfileChanges(
        name = name.takeIf { it != original?.name },
        photoUrl = if (removePhoto) "" else uploadedUrl,
    )

    override fun toString(): String = "EditProfileState(stage=$stage)"
}
