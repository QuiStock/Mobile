package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.LogoutRequest
import com.quistock.quistock.domain.port.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogoutTests {
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val preferences = mockk<UserPreferences>(relaxed = true)
    private val session = SessionUseCase(auth, preferences)

    private suspend fun login() {
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        session.login("synthetic", "synthetic")
    }

    @Test fun logoutFencesAccessAndClearsLocallyBeforeRemoteCompletes() = runTest {
        login()
        val old = session.snapshot()
        val remote = CompletableDeferred<Boolean>()
        every { auth.prepareLogout(old.generation) } returns LogoutRequest { remote.await() }
        val operation = async { session.logout() }
        yield()
        assertEquals(SessionState.LoggingOut, session.state.value)
        assertTrue(session.snapshot().generation > old.generation)
        assertTrue(runCatching { session.requireAccess(old) }.isFailure)
        coVerify { auth.clear(old.generation + 1) }
        coVerify { preferences.clearUserId() }
        remote.complete(false)
        operation.await()
        assertEquals(SessionState.LoggedOut(false), session.state.value)
    }

    @Test fun localFailureRetriesCleanupWithoutRepeatingRemoteLogout() = runTest {
        login()
        var calls = 0
        every { auth.prepareLogout(any()) } returns LogoutRequest { calls++; true }
        coEvery { preferences.clearUserId() } throws IllegalStateException()
        session.logout()
        assertEquals(SessionState.LogoutFailed(true), session.state.value)
        coEvery { preferences.clearUserId() } returns Unit
        session.retryLogoutCleanup()
        assertEquals(SessionState.LoggedOut(true), session.state.value)
        assertEquals(1, calls)
    }

    @Test fun lateRefreshCannotReactivateLoggedOutSession() = runTest {
        login()
        val old = session.snapshot()
        val response = CompletableDeferred<com.quistock.quistock.domain.model.AuthRefreshResult>()
        coEvery { auth.refresh(old.generation) } coAnswers { response.await() }
        every { auth.prepareLogout(any()) } returns LogoutRequest { true }
        val refresh = async { session.refresh(old) }
        yield()
        session.logout()
        response.complete(AuthResult.Success)
        refresh.await()
        assertEquals(SessionState.LoggedOut(true), session.state.value)
        session.refresh(session.snapshot())
        coVerify(exactly = 1) { auth.refresh(any()) }
    }
}
