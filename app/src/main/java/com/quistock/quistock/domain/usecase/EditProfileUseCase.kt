package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.port.ProfileEditRepository
import com.quistock.quistock.domain.port.ProfileRepository

class EditProfileUseCase(
    private val repository: ProfileEditRepository,
    private val reader: ProfileRepository,
    private val profile: ProfileUseCase,
    private val photo: LoadProfilePhotoUseCase,
    private val session: SessionUseCase,
) {
    suspend fun save(changes: ProfileChanges, expected: SessionSnapshot): ProfileEditResult {
        session.requireAccess(expected)
        return if (changes.isValid()) {
            publish(repository.update(changes, expected), expected)
        } else {
            ProfileEditResult.Rejected(invalidInput = true)
        }
    }

    suspend fun confirm(changes: ProfileChanges, expected: SessionSnapshot): ProfileEditResult {
        session.requireAccess(expected)
        val result = when (val read = reader.read()) {
            is ProfileResult.Success -> if (changes.matches(read.profile)) {
                ProfileEditResult.Saved(read.profile)
            } else {
                ProfileEditResult.Rejected()
            }

            ProfileResult.Forbidden, ProfileResult.NoActiveStore -> ProfileEditResult.Forbidden

            is ProfileResult.Failure -> ProfileEditResult.CheckLater(read.retryAfterMillis)
        }
        return publish(result, expected)
    }

    private suspend fun publish(result: ProfileEditResult, expected: SessionSnapshot): ProfileEditResult =
        when (result) {
            is ProfileEditResult.Saved -> {
                publishSaved(result, expected)
            }

            ProfileEditResult.Forbidden -> {
                session.expire(expected)
                result
            }

            else -> result
        }

    private suspend fun publishSaved(result: ProfileEditResult.Saved, expected: SessionSnapshot): ProfileEditResult {
        val rejection = result.profile.rejection()
        return when {
            rejection != null && rejection != ProfileRejection.INVALID -> {
                session.expire(expected)
                ProfileEditResult.Forbidden
            }

            profile.publishEdit(result.profile, expected) { photo.clear() } -> result

            else -> ProfileEditResult.Uncertain
        }
    }
}
