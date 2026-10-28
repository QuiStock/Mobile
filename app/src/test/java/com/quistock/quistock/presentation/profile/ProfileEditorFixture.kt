package com.quistock.quistock.presentation.profile

import androidx.lifecycle.ViewModelStore
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

internal class ProfileEditorFixture {
    val user = UserProfile(
        "fixture",
        "Fixture",
        "fixture@example.test",
        UserRole.EMPLOYEE,
        UserStatus.ACTIVE,
        "https://images.example.test/old.jpg",
        1,
        "Store",
    )
    val expected = SessionSnapshot(1, true)
    val status = MutableStateFlow<SessionState>(SessionState.Active)
    val profiles = MutableStateFlow<ProfileState>(ProfileState.Ready(1, user))
    val editor = mockk<EditProfileUseCase>()
    val photos = mockk<EditProfilePhotoUseCase>()
    val session = mockk<SessionUseCase>()
    private val profile = mockk<ProfileUseCase>()
    private val store = ViewModelStore()

    init {
        every { session.snapshot() } returns expected
        every { session.state } returns status
        coEvery { session.requireAccess(any()) } returns expected
        every { profile.state } returns profiles
        every { photos.available } returns true
    }

    fun model(): EditProfileViewModel = EditProfileViewModel(editor, photos, profile, session)
        .also { store.put("editor", it) }

    fun close() = store.clear()
}
