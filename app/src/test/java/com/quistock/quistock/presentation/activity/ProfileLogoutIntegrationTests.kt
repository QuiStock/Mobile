package com.quistock.quistock.presentation.activity

import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.usecase.LoadProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileLogoutIntegrationTests {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun logoutClearsRejectionInsteadOfCarryingItIntoTheLoginScreen() = runTest {
        val sessionState = MutableStateFlow<SessionState>(SessionState.Expired)
        val rejection = ProfileState.Rejected(ProfileRejection.NO_STORE, UserRole.EMPLOYEE)
        val profileState = MutableStateFlow<ProfileState>(rejection)
        val session = mockk<SessionUseCase>()
        every { session.state } returns sessionState
        every { session.retryDelayMillis } returns 0L
        coEvery { session.restore() } returns SessionState.Expired
        val profile = mockk<ProfileUseCase>()
        every { profile.state } returns profileState
        every { profile.clear() } answers { profileState.value = ProfileState.Empty }
        val photos = mockk<LoadProfilePhotoUseCase>()
        every { photos.clear() } returns Unit
        SessionViewModel(session, profile, photos, mockk(relaxed = true))
        runCurrent()
        assertSame(rejection, profileState.value)

        sessionState.value = SessionState.LoggingOut
        runCurrent()

        assertSame(ProfileState.Empty, profileState.value)
        verify(exactly = 1) { profile.clear() }
        verify(exactly = 2) { photos.clear() }
    }
}
