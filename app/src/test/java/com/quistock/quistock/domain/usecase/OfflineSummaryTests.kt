package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummary
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.HomeSummaryCache
import com.quistock.quistock.domain.port.HomeSummaryRepository
import com.quistock.quistock.domain.port.StoredSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OfflineSummaryTests {
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val session = SessionUseCase(auth)
    private val profile = mockk<ProfileUseCase>(relaxed = true)
    private val cache = mockk<HomeSummaryCache>()
    private val remote = mockk<HomeSummaryRepository>()
    private val summary = GetHomeSummaryUseCase(remote, cache, session, profile)
    private val binding = SessionBinding("local-session", "user", 51, System.currentTimeMillis() + 60_000)
    private val saved = CachedHomeSummary(HomeSummary(4, 2, 3, 5), System.currentTimeMillis())

    private fun prepare(error: AuthRefreshError = AuthRefreshError.NetworkError) {
        coEvery { auth.restore(any()) } returns StoredSession.AVAILABLE
        coEvery { auth.refresh(any()) } returns error
        every { auth.currentBinding(any()) } returns binding
        every { auth.hasAccess(any()) } returns false
        coEvery { cache.read(binding) } returns saved
    }

    @Test fun coldNetworkFailurePermitsOnlySavedIndicatorsAndCannotAuthorizeCore() = runTest {
        prepare()
        session.restore()
        assertTrue(summary.openOffline())
        assertTrue(session.state.value is SessionState.OfflineReadOnly)
        assertEquals(saved, summary.cached())
        assertTrue(runCatching { session.requireAccess(session.snapshot()) }.isFailure)
        coVerify(exactly = 0) { remote.read(any()) }
    }

    @Test fun missingCacheBindingAndProtocolFailureCannotUnlockHome() = runTest {
        prepare(AuthRefreshError.ProtocolError)
        session.restore()
        assertFalse(summary.openOffline())
        coVerify(exactly = 0) { cache.read(any()) }
    }

    @Test fun missingCacheCannotUnlockHomeEvenWithValidStoredSession() = runTest {
        prepare()
        coEvery { cache.read(binding) } returns null
        session.restore()
        assertFalse(summary.openOffline())
        every { auth.currentBinding(any()) } returns null
        assertFalse(summary.openOffline())
    }

    @Test fun anExpiredOrNonPersistentBindingCannotUnlockHome() = runTest {
        prepare()
        session.restore()
        every { auth.currentBinding(any()) } returns binding.copy(expiresAt = 1)
        assertFalse(summary.openOffline())
        every { auth.currentBinding(any()) } returns binding.copy(persisted = false)
        assertFalse(summary.openOffline())
    }

    @Test fun terminalRefreshCannotUseSavedIndicators() = runTest {
        prepare(AuthRefreshError.InvalidToken)
        session.restore()
        assertEquals(SessionState.Expired, session.state.value)
        assertFalse(summary.openOffline())
        coVerify { auth.clear(any()) }
    }

    @Test fun anOldOfflineTimerCannotExpireASuccessfullyRecoveredSession() = runTest {
        prepare()
        session.restore()
        summary.openOffline()
        val previous = session.snapshot()
        coEvery { auth.refresh(any()) } returns com.quistock.quistock.domain.model.AuthResult.Success
        every { auth.hasAccess(any()) } returns true
        session.refresh(previous)
        session.local.expireOffline(previous)
        assertEquals(SessionState.Active, session.state.value)
        coVerify(exactly = 0) { auth.clear(any()) }
    }

    @Test fun localExpiryRevokesOfflineEligibilityAndClearsTheSavedSession() = runTest {
        prepare()
        session.restore()
        summary.openOffline()
        val previous = session.snapshot()
        every { auth.currentBinding(any()) } returns binding.copy(expiresAt = 1)
        session.local.expireOffline(previous)
        assertEquals(SessionState.Expired, session.state.value)
        coVerify { auth.clear(previous.generation) }
    }

    @Test fun onlyTransientProfileFailureCanOpenSavedIndicators() = runTest {
        prepare()
        coEvery { auth.refresh(any()) } returns com.quistock.quistock.domain.model.AuthResult.Success
        every { auth.hasAccess(any()) } returns true
        session.restore()
        val state = kotlinx.coroutines.flow.MutableStateFlow<ProfileState>(ProfileState.Failure())
        every { profile.state } returns state
        assertFalse(summary.openOffline())
        coVerify(exactly = 0) { cache.read(any()) }
        state.value = ProfileState.Failure(transient = true)
        assertTrue(summary.openOffline())
    }

    @Test fun freshnessUsesSaoPauloAndClockRollbackIsStale() {
        val received = Instant.parse("2026-10-29T02:59:00Z").toEpochMilli()
        val value = saved.copy(receivedAt = received)
        assertFalse(value.staleAt(Instant.parse("2026-10-29T02:59:59Z").toEpochMilli()))
        assertTrue(value.staleAt(Instant.parse("2026-10-29T03:00:00Z").toEpochMilli()))
        assertTrue(value.staleAt(Instant.parse("2026-10-28T02:00:00Z").toEpochMilli()))
    }
}
