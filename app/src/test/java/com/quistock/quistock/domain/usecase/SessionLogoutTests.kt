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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
        runCurrent()
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
        every { auth.prepareLogout(any()) } returns LogoutRequest {
            calls++
            true
        }
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
        runCurrent()
        session.logout()
        response.complete(AuthResult.Success)
        refresh.await()
        assertEquals(SessionState.LoggedOut(true), session.state.value)
        session.refresh(session.snapshot())
        coVerify(exactly = 1) { auth.refresh(any()) }
    }

    @Test fun concurrentLogoutIsSharedAndBlocksNewLogin() = runTest {
        login()
        val remote = CompletableDeferred<Boolean>()
        var calls = 0
        every { auth.prepareLogout(any()) } returns LogoutRequest {
            calls++
            remote.await()
        }
        val first = async { session.logout() }
        val second = async { session.logout() }
        runCurrent()
        assertEquals(
            com.quistock.quistock.domain.model.LoginError.UnexpectedError,
            session.login("replacement", "synthetic"),
        )
        remote.complete(true)
        first.await()
        second.await()
        assertEquals(1, calls)
        assertEquals(SessionState.LoggedOut(true), session.state.value)
    }

    @Test fun lateLoginCannotReactivateAfterLogout() = runTest {
        val response = CompletableDeferred<com.quistock.quistock.domain.model.LoginResult>()
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } coAnswers { response.await() }
        every { auth.prepareLogout(any()) } returns LogoutRequest { true }
        val login = async { session.login("synthetic", "synthetic") }
        runCurrent()
        session.logout()
        response.complete(AuthResult.Success)
        login.await()
        assertEquals(SessionState.LoggedOut(true), session.state.value)
    }

    @Test fun credentialFailureStillAttemptsIdentityRemovalAndRemoteLogout() = runTest {
        login()
        val generation = session.snapshot().generation + 1
        every { auth.prepareLogout(any()) } returns LogoutRequest { false }
        coEvery { auth.clear(generation) } throws IllegalStateException()
        session.logout()
        coVerify { preferences.clearUserId() }
        assertEquals(SessionState.LogoutFailed(false), session.state.value)
    }
}
