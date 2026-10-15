package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.StoredSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionUseCaseTests {
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val session = SessionUseCase(auth)
    private fun active() {
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
    }

    @Test fun deletionFailureStillFencesOldResponses() = runTest {
        active()
        session.login("synthetic", "synthetic")
        val old = session.snapshot()
        coEvery { auth.clear(old.generation) } throws IllegalStateException()
        session.expire(old)
        assertEquals(SessionState.LocalFailure, session.state.value)
        coVerify { auth.begin(old.generation + 1) }
        assertTrue(runCatching { session.requireAccess(old) }.isFailure)
    }

    @Test fun successfulLoginPublishesSecretFreeSnapshotAfterRepositoryCommit() = runTest {
        active()
        assertEquals(AuthResult.Success, session.login("synthetic@example.com", "synthetic"))
        assertTrue(session.snapshot().hasAccess)
        assertEquals(SessionState.Active, session.state.value)
        coVerifyOrder {
            auth.begin(1)
            auth.clear(1)
            auth.login(any(), any(), 1)
        }
    }

    @Test fun missingCredentialDoesNotRefreshOrShowExpiration() = runTest {
        coEvery { auth.restore(any()) } returns StoredSession.MISSING
        assertEquals(SessionState.SignedOut, session.restore())
        coVerify(exactly = 0) { auth.refresh(any()) }
    }

    @Test fun expiredCredentialShowsExpirationAndRevokesCredential() = runTest {
        coEvery { auth.restore(any()) } returns StoredSession.EXPIRED
        assertEquals(SessionState.Expired, session.restore())
        coVerify(exactly = 1) { auth.clear(1) }
        coVerify(exactly = 1) { auth.begin(2) }
        coVerify(exactly = 0) { auth.refresh(any()) }
    }

    @Test fun unreadableCredentialBlocksRestoration() = runTest {
        coEvery { auth.restore(any()) } throws IllegalStateException()
        assertEquals(SessionState.LocalFailure, session.restore())
        coVerify(exactly = 0) { auth.refresh(any()) }
    }

    @Test fun partialInstallationDoesNotPublishSuccess() = runTest {
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        every { auth.hasAccess(any()) } returns false
        assertEquals(LoginError.UnexpectedError, session.login("synthetic", "synthetic"))
        assertEquals(SessionState.LocalFailure, session.state.value)
        assertTrue(runCatching { session.requireAccess(session.snapshot()) }.isFailure)
    }

    @Test fun concurrentRefreshIsSharedAndLateUnauthorizedReusesRevision() = runTest {
        active()
        session.login("synthetic", "synthetic")
        val old = session.snapshot()
        val pending = CompletableDeferred<AuthRefreshResult>()
        coEvery { auth.refresh(any()) } coAnswers { pending.await() }
        val first = async { session.refresh(old) }
        val second = async { session.refresh(old) }
        yield()
        pending.complete(AuthResult.Success)
        assertEquals(old.revision + 1, first.await().revision)
        assertEquals(first.await(), second.await())
        session.refresh(old)
        coVerify(exactly = 1) { auth.refresh(any()) }
    }

    @Test fun oldRefreshCannotRevokeOrReplaceNewLogin() = runTest {
        for (result in listOf(AuthRefreshError.InvalidToken, AuthResult.Success)) {
            active()
            session.login("old", "synthetic")
            val pending = CompletableDeferred<AuthRefreshResult>()
            coEvery { auth.refresh(any()) } coAnswers { pending.await() }
            val operation = async { session.refresh(session.snapshot()) }
            yield()
            session.login("new", "synthetic")
            val replacement = session.snapshot()
            pending.complete(result)
            operation.await()
            assertEquals(replacement, session.snapshot())
            assertEquals(SessionState.Active, session.state.value)
        }
    }

    @Test fun transientRestorationPreservesCredential() = runTest {
        coEvery { auth.restore(any()) } returns StoredSession.AVAILABLE
        coEvery { auth.refresh(any()) } returns AuthRefreshError.RateLimited
        assertEquals(SessionState.Failure(SessionFailure.RATE_LIMIT), session.restore())
        coVerify(exactly = 0) { auth.clear(any()) }
    }

    @Test fun sharedRejectionExpiresOnlyOnce() = runTest {
        active()
        session.login("synthetic", "synthetic")
        val expected = session.snapshot()
        val pending = CompletableDeferred<AuthRefreshResult>()
        coEvery { auth.refresh(any()) } coAnswers { pending.await() }
        val first = async { session.refresh(expected) }
        val second = async { session.refresh(expected) }
        yield()
        pending.complete(AuthRefreshError.InvalidToken)
        first.await()
        second.await()
        assertEquals(SessionState.Expired, session.state.value)
        coVerify(exactly = 2) { auth.clear(expected.generation) }
        coVerify(exactly = 1) { auth.begin(expected.generation + 1) }
    }

    @Test fun localFailureAfterRotationRequiresLoginAndBlocksAccess() = runTest {
        active()
        session.login("synthetic", "synthetic")
        coEvery { auth.refresh(any()) } returns AuthRefreshError.UnexpectedError
        session.refresh(session.snapshot())
        assertEquals(SessionState.LocalFailure, session.state.value)
        assertTrue(runCatching { session.requireAccess(session.snapshot()) }.isFailure)
    }

    @Test fun oldGenerationCannotRunProtectedAction() = runTest {
        active()
        session.login("old", "synthetic")
        val old = session.snapshot()
        session.login("new", "synthetic")
        var executed = false
        assertTrue(runCatching { session.withCurrentSession(old) { executed = true } }.isFailure)
        assertFalse(executed)
    }
}
