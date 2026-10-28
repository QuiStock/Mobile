package com.quistock.quistock.presentation.profile

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.ProfilePhotoUploadResult
import com.quistock.quistock.domain.usecase.EditProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.EditProfileUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditSubmissionTests {
    private val editor = mockk<EditProfileUseCase>()
    private val photos = mockk<EditProfilePhotoUseCase>()
    private val original = UserProfile(
        "1",
        "Fixture",
        "fixture@example.test",
        UserRole.EMPLOYEE,
        UserStatus.ACTIVE,
        null,
        1,
        "Store",
    )
    private val jpeg = byteArrayOf(1, 2)
    private val state = MutableStateFlow(EditProfileState(original, photo = ProfileEditPhoto(jpeg)))
    private val submission = ProfileEditSubmission(editor, photos, SessionSnapshot(1, true), state)

    @Test fun uploadFailureNeverCallsCore() = runTest {
        coEvery { photos.upload(any()) } returns ProfilePhotoUploadResult.Failed()
        submission.save()
        assertSame(ProfileEditMessage.UPLOAD, state.value.message)
        assertSame(jpeg, state.value.jpeg)
        coVerify(exactly = 0) { editor.save(any(), any()) }
    }

    @Test fun definitiveFailureReusesUploadedUrlOnManualRetry() = runTest {
        coEvery { photos.upload(any()) } returns ProfilePhotoUploadResult.Uploaded("https://example.test/new.jpg")
        coEvery { editor.save(any(), any()) } returns ProfileEditResult.Rejected()
        submission.save()
        submission.save()
        coVerify(exactly = 1) { photos.upload(any()) }
        coVerify(exactly = 2) { editor.save(match { it.photoUrl == "https://example.test/new.jpg" }, any()) }
        assertFalse(state.value.saved)
    }

    @Test fun uncertainWriteOnlyRepeatsReadUntilConfirmed() = runTest {
        state.value = EditProfileState(original, name = "New")
        coEvery { editor.save(any(), any()) } returns ProfileEditResult.Uncertain
        coEvery { editor.confirm(any(), any()) } returns ProfileEditResult.Uncertain
        submission.save()
        assertSame(ProfileEditMessage.UNCERTAIN, state.value.message)
        submission.confirm()
        coVerify(exactly = 1) { editor.save(any(), any()) }
        coVerify(exactly = 2) { editor.confirm(any(), any()) }
        coVerify(exactly = 0) { photos.upload(any()) }
    }

    @Test fun divergentConfirmationUnlocksManualEditing() = runTest {
        state.value = EditProfileState(original, name = "New", pending = ProfileChanges(name = "New"))
        coEvery { editor.confirm(any(), any()) } returns ProfileEditResult.Rejected()
        submission.confirm()
        assertNull(state.value.pending)
        assertEquals("New", state.value.name)
    }

    @Test fun rateLimitSetsDeadlineWithoutSubmittingAgain() = runTest {
        state.value = EditProfileState(original, name = "New")
        coEvery { editor.save(any(), any()) } returns ProfileEditResult.Rejected(retryAfterMillis = 1000)
        submission.save()
        assertTrue(state.value.retryAt > profileEditNowMillis())
        coVerify(exactly = 1) { editor.save(any(), any()) }
    }

    @Test fun uploadRateLimitPreservesDraftAndSetsDeadlineWithoutPatch() = runTest {
        coEvery { photos.upload(any()) } returns ProfilePhotoUploadResult.Failed(retryAfterMillis = 1000)
        submission.save()
        assertTrue(state.value.retryAt > profileEditNowMillis())
        assertFalse(state.value.busy)
        assertSame(jpeg, state.value.jpeg)
        coVerify(exactly = 0) { editor.save(any(), any()) }
    }

    @Test fun unchangedDraftCannotSaveAndRemovalIsAnEmptyString() {
        assertFalse(EditProfileState(original).canSave)
        val removed = EditProfileState(original, removePhoto = true)
        assertEquals("", removed.changes().photoUrl)
        assertNull(removed.changes().name)
    }
}
