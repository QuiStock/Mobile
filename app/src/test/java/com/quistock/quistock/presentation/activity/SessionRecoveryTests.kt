package com.quistock.quistock.presentation.activity

import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.SessionUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.LoadProfilePhotoUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRecoveryTests {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun protocolRecoveryOnlyStartsOnClickAndRejectsDuplicateClicks() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Failure(SessionFailure.PROTOCOL))
        val session = mockk<SessionUseCase>(relaxed = true)
        every { session.state } returns state
        every { session.snapshot() } returns SessionSnapshot(1, false)
        every { session.retryDelayMillis } returns 0L
        coEvery { session.restore() } returns state.value
        val released = CompletableDeferred<Unit>()
        coEvery { session.refresh(any()) } coAnswers {
            released.await()
            state.value = SessionState.Active
            SessionSnapshot(1, true, 1)
        }
        val profile = profileFixture()
        val viewModel = SessionViewModel(
            session,
            profile,
            LoadProfilePhotoUseCase(mockk(relaxed = true), profile, session),
        )
        runCurrent()
        coVerify(exactly = 0) { session.refresh(any()) }
        viewModel.retrySession()
        viewModel.retrySession()
        runCurrent()
        assertTrue(viewModel.recovery.value.retrying)
        coVerify(exactly = 1) { session.refresh(any()) }
        released.complete(Unit)
        runCurrent()
        assertFalse(viewModel.recovery.value.retrying)
        assertEquals(SessionState.Active, state.value)
        coVerify(exactly = 1) { profile.load() }
        assertEquals(R.string.session_renewal_failed, sessionFailureMessage(SessionFailure.PROTOCOL))
    }

    @Test fun retryAfterExpiryEnablesActionWithoutSendingRequests() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Failure(SessionFailure.RATE_LIMIT))
        val session = mockk<SessionUseCase>(relaxed = true)
        every { session.state } returns state
        every { session.retryDelayMillis } returns 1000L
        coEvery { session.restore() } returns state.value
        val profile = profileFixture()
        val viewModel = SessionViewModel(
            session,
            profile,
            LoadProfilePhotoUseCase(mockk(relaxed = true), profile, session),
        )
        runCurrent()
        assertTrue(viewModel.recovery.value.retryBlocked)
        viewModel.retrySession()
        coVerify(exactly = 0) { session.refresh(any()) }
        advanceTimeBy(1000)
        runCurrent()
        assertFalse(viewModel.recovery.value.retryBlocked)
        coVerify(exactly = 0) { session.refresh(any()) }
        coVerify(exactly = 0) { profile.load() }
    }

    private fun profileFixture(): ProfileUseCase {
        val profile = mockk<ProfileUseCase>()
        every { profile.state } returns MutableStateFlow<ProfileState>(ProfileState.Empty)
        every { profile.clear() } returns Unit
        coEvery { profile.load() } returns Unit
        return profile
    }

}
