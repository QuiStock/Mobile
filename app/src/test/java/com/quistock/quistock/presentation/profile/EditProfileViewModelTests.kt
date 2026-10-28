package com.quistock.quistock.presentation.profile

import androidx.lifecycle.ViewModelStore
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.usecase.EditProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.EditProfileUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTests {
    private val store = ViewModelStore()
    private val session = mockk<SessionUseCase>()
    private val profile = mockk<ProfileUseCase>()
    private val editor = mockk<EditProfileUseCase>()
    private val photos = mockk<EditProfilePhotoUseCase>()
    private val status = MutableStateFlow<SessionState>(SessionState.Active)
    private val user = UserProfile(
        "1",
        "Fixture",
        "fixture@example.test",
        UserRole.EMPLOYEE,
        UserStatus.ACTIVE,
        null,
        1,
        "Store",
    )

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { session.snapshot() } returns SessionSnapshot(1, true)
        every { session.state } returns status
        coEvery { session.requireAccess(any()) } returns SessionSnapshot(1, true)
        every { profile.state } returns MutableStateFlow<ProfileState>(ProfileState.Ready(1, user))
        every { photos.available } returns true
    }

    @After fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(): EditProfileViewModel = EditProfileViewModel(editor, photos, profile, session)
        .also { store.put("editor", it) }

    @Test fun repeatedSaveAndEditsAreBlockedDuringSubmission() = runTest {
        val response = CompletableDeferred<ProfileEditResult>()
        coEvery { editor.save(any(), any()) } coAnswers { response.await() }
        val model = model()
        runCurrent()
        model.rename("Updated")
        model.save()
        model.save()
        runCurrent()
        model.rename("Ignored")
        assertTrue(model.state.value.busy)
        assertEquals("Updated", model.state.value.name)
        coVerify(exactly = 1) { editor.save(any(), any()) }
        response.complete(ProfileEditResult.Rejected())
        runCurrent()
        assertFalse(model.state.value.busy)
    }

    @Test fun logoutClearsDraftAndCancelsPendingResult() = runTest {
        val response = CompletableDeferred<ProfileEditResult>()
        coEvery { editor.save(any(), any()) } coAnswers { response.await() }
        val model = model()
        runCurrent()
        model.rename("Old account")
        model.save()
        runCurrent()
        status.value = SessionState.LoggingOut
        runCurrent()
        response.complete(ProfileEditResult.Saved(user))
        runCurrent()
        assertNull(model.state.value.original)
        assertEquals("", model.state.value.name)
        assertFalse(model.state.value.saved)
    }

    @Test fun missingUploadConfigurationPreservesNameEditingAndRemoval() = runTest {
        every { photos.available } returns false
        val model = model()
        runCurrent()
        assertFalse(model.uploadAvailable)
        model.rename("Updated")
        assertTrue(model.state.value.canSave)
        coVerify(exactly = 0) { photos.upload(any()) }
    }
}
