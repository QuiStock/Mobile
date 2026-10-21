package com.quistock.quistock.presentation.activity

import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.ProfileRepository
import com.quistock.quistock.domain.usecase.LoadProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRecoveryTests {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun profileRateLimitEnablesManualRetryWithoutAutomaticRequestOrNewLogin() = runTest {
        val auth = mockk<AuthRepository>(relaxed = true)
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        val session = SessionUseCase(auth)
        session.login("fixture@example.test", "fixture")
        val repository = mockk<ProfileRepository>()
        coEvery { repository.read() } returnsMany listOf(
            ProfileResult.Failure(1000),
            ProfileResult.Success(
                UserProfile(
                    "1", "Fixture", "fixture@example.test", UserRole.EMPLOYEE, UserStatus.ACTIVE, null, 1, "Fixture",
                ),
            ),
        )
        val profile = ProfileUseCase(repository, session)
        val model = SessionViewModel(session, profile, LoadProfilePhotoUseCase(mockk(relaxed = true), profile, session))
        runCurrent()
        assertTrue(model.recovery.value.retryBlocked)
        model.retrySession()
        coVerify(exactly = 1) { repository.read() }
        advanceTimeBy(1000)
        runCurrent()
        assertFalse(model.recovery.value.retryBlocked)
        coVerify(exactly = 1) { repository.read() }
        model.retrySession()
        runCurrent()
        assertTrue(profile.state.value is ProfileState.Ready)
        coVerify(exactly = 2) { repository.read() }
        coVerify(exactly = 1) { auth.login(any(), any(), any()) }
    }
}
