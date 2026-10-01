package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.AuthTokens
import com.quistock.quistock.domain.model.RefreshToken
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.SecretStorage
import com.quistock.quistock.domain.port.delete
import com.quistock.quistock.domain.port.read
import com.quistock.quistock.domain.port.save
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionUseCaseTests {
    private val auth = mockk<AuthRepository>()
    private val storage = mockk<SecretStorage>(relaxed = true)
    private val cache = mockk<CachedBigNumbersRepository>(relaxed = true)
    private val tokens = AuthTokens(AccessToken("synthetic-access"), RefreshToken("opaque-not-uuid"))

    @Test fun delayedStorageReadFailureCannotReplaceNewLogin() = runTest {
        val pending = CompletableDeferred<RefreshToken?>()
        coEvery { storage.read(RefreshToken::class) } coAnswers { pending.await() }
        val session = SessionUseCase(auth, storage, cache)
        val restoration = async { session.restore() }
        yield()
        assertTrue(session.install(tokens))
        pending.completeExceptionally(IllegalStateException())
        assertEquals(SessionState.Active, restoration.await())
        assertEquals(tokens.accessToken, session.requireAccess(session.snapshot()).accessToken)
        coVerify(exactly = 0) { auth.refresh(any()) }
    }

    @Test fun missingTokenDoesNotRefresh() = runTest {
        coEvery { storage.read(RefreshToken::class) } returns null
        val session = SessionUseCase(auth, storage, cache)
        assertEquals(SessionState.SignedOut, session.restore())
        coVerify(exactly = 0) { auth.refresh(any()) }
    }

    @Test fun storageFailureDoesNotPublishPartialSession() = runTest {
        coEvery { storage.save(any<AccessToken>(), AccessToken::class) } throws IllegalStateException()
        val session = SessionUseCase(auth, storage, cache)
        assertFalse(session.install(tokens))
        assertNull(session.snapshot().accessToken)
        coVerify { storage.delete(RefreshToken::class) }
    }

    @Test fun concurrentRefreshIsSharedAndLateUnauthorizedUsesCurrentToken() = runTest {
        val pending = CompletableDeferred<AuthRefreshResult>()
        coEvery { auth.refresh(tokens.refreshToken) } coAnswers { pending.await() }
        val session = SessionUseCase(auth, storage, cache)
        session.install(tokens)
        val old = session.snapshot()
        val first = async { session.refresh(old) }
        val second = async { session.refresh(old) }
        yield()
        val renewed = AuthTokens(AccessToken("renewed"), RefreshToken("rotated"))
        pending.complete(AuthResult.Success(renewed))
        assertEquals(renewed.accessToken, first.await().accessToken)
        assertEquals(renewed.accessToken, second.await().accessToken)
        assertEquals(renewed.accessToken, session.refresh(old).accessToken)
        coVerify(exactly = 1) { auth.refresh(any()) }
    }

    @Test fun oldRefreshCannotRevokeNewLogin() = runTest {
        val pending = CompletableDeferred<AuthRefreshResult>()
        coEvery { auth.refresh(any()) } coAnswers { pending.await() }
        val session = SessionUseCase(auth, storage, cache)
        session.install(tokens)
        val old = session.snapshot()
        val refresh = async { session.refresh(old) }
        yield()
        val newTokens = AuthTokens(AccessToken("new-account"), RefreshToken("new-refresh"))
        session.install(newTokens)
        pending.complete(AuthRefreshError.InvalidToken)
        refresh.await()
        assertEquals(newTokens.accessToken, session.snapshot().accessToken)
        coVerify(exactly = 2) { cache.clear() }
    }

    @Test fun sharedTransientFailurePreservesSessionWithoutAutomaticRetry() = runTest {
        val pending = CompletableDeferred<AuthRefreshResult>()
        coEvery { auth.refresh(any()) } coAnswers { pending.await() }
        val session = SessionUseCase(auth, storage, cache)
        session.install(tokens)
        val expected = session.snapshot()
        val first = async { session.refresh(expected) }
        val second = async { session.refresh(expected) }
        yield()
        pending.complete(AuthRefreshError.NetworkError)
        assertEquals(expected, first.await())
        assertEquals(expected, second.await())
        assertEquals(
            SessionState.Failure(com.quistock.quistock.domain.model.SessionFailure.NETWORK),
            session.state.value,
        )
        coVerify(exactly = 1) { auth.refresh(any()) }
        coVerify(exactly = 0) { storage.delete(RefreshToken::class) }
        coVerify(exactly = 1) { cache.clear() }
    }

    @Test fun lateUnauthorizedDoesNotRefreshAgainWhenMockReturnsSameTokens() = runTest {
        coEvery { auth.refresh(any()) } returns AuthResult.Success(tokens)
        val session = SessionUseCase(auth, storage, cache)
        session.install(tokens)
        val old = session.snapshot()
        session.refresh(old)
        session.refresh(old)
        coVerify(exactly = 1) { auth.refresh(any()) }
    }

    @Test fun persistedTokenRestoresWithoutCredentials() = runTest {
        coEvery { storage.read(RefreshToken::class) } returns tokens.refreshToken
        coEvery { auth.refresh(tokens.refreshToken) } returns AuthResult.Success(tokens)
        val session = SessionUseCase(auth, storage, cache)
        assertEquals(SessionState.Active, session.restore())
        assertEquals(tokens.accessToken, session.snapshot().accessToken)
        coVerify(exactly = 0) { auth.login(any(), any()) }
        coVerify(exactly = 0) { cache.clear() }
    }

    @Test fun unreadableTokenReportsUnexpectedWithoutRefresh() = runTest {
        coEvery { storage.read(RefreshToken::class) } throws IllegalStateException()
        val session = SessionUseCase(auth, storage, cache)
        assertEquals(
            SessionState.LocalFailure,
            session.restore(),
        )
        coVerify(exactly = 0) { auth.refresh(any()) }
    }

    @Test fun recoveryTransientFailuresPreserveRefreshAndCache() = runTest {
        for ((error, reason) in listOf(
            AuthRefreshError.NetworkError to com.quistock.quistock.domain.model.SessionFailure.NETWORK,
            AuthRefreshError.Timeout to com.quistock.quistock.domain.model.SessionFailure.TIMEOUT,
            AuthRefreshError.ServerError to com.quistock.quistock.domain.model.SessionFailure.SERVER,
        )) {
            coEvery { storage.read(RefreshToken::class) } returns tokens.refreshToken
            coEvery { auth.refresh(any()) } returns error
            val session = SessionUseCase(auth, storage, cache)
            assertEquals(SessionState.Failure(reason), session.restore())
            assertNull(session.snapshot().accessToken)
        }
        coVerify(exactly = 3) { auth.refresh(tokens.refreshToken) }
        coVerify(exactly = 0) { storage.delete(RefreshToken::class) }
        coVerify(exactly = 0) { cache.clear() }
    }

    @Test fun oldSuccessfulRefreshCannotOverwriteNewTokens() = runTest {
        val pending = CompletableDeferred<AuthRefreshResult>()
        coEvery { auth.refresh(any()) } coAnswers { pending.await() }
        val session = SessionUseCase(auth, storage, cache)
        session.install(tokens)
        val old = session.snapshot()
        val refresh = async { session.refresh(old) }
        yield()
        val replacement = AuthTokens(AccessToken("different-account"), RefreshToken("different-refresh"))
        session.install(replacement)
        pending.complete(AuthResult.Success(AuthTokens(AccessToken("late"), RefreshToken("late-refresh"))))
        refresh.await()
        assertEquals(replacement.accessToken, session.snapshot().accessToken)
        coVerify(exactly = 0) { storage.save(AccessToken("late"), AccessToken::class) }
    }

    @Test fun expiredGenerationCannotWriteCache() = runTest {
        val session = SessionUseCase(auth, storage, cache)
        session.install(tokens)
        val old = session.snapshot()
        session.expire(old)
        session.install(tokens)
        assertTrue(
            runCatching { session.withCurrentSession(old) { error("must not run") } }.exceptionOrNull() is
                com.quistock.quistock.domain.model.SessionException,
        )
    }

    @Test fun refreshTokenWriteFailureAlsoBlocksRemoteAccess() = runTest {
        coEvery { storage.save(any<RefreshToken>(), RefreshToken::class) } throws IllegalStateException()
        val session = SessionUseCase(auth, storage, cache)
        assertFalse(session.install(tokens))
        assertNull(session.snapshot().accessToken)
        assertTrue(runCatching { session.requireAccess(session.snapshot()) }.isFailure)
        coVerify(exactly = 0) { storage.save(any<AccessToken>(), AccessToken::class) }
    }

    @Test fun sharedRejectionExpiresOnce() = runTest {
        val result = CompletableDeferred<AuthRefreshResult>()
        coEvery { auth.refresh(any()) } coAnswers { result.await() }
        val session = SessionUseCase(auth, storage, cache)
        session.install(tokens)
        val old = session.snapshot()
        val first = async { session.refresh(old) }
        val second = async { session.refresh(old) }
        yield()
        result.complete(AuthRefreshError.InvalidToken)
        first.await()
        second.await()
        assertEquals(SessionState.Expired, session.state.value)
        coVerify(exactly = 1) { auth.refresh(any()) }
        coVerify(exactly = 2) { cache.clear() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun elapsedTimeDoesNotRefreshOrParseTokens() = runTest {
        val session = SessionUseCase(auth, storage, cache)
        session.install(tokens)
        advanceTimeBy(172800000)
        advanceUntilIdle()
        assertEquals(tokens.accessToken, session.snapshot().accessToken)
        coVerify(exactly = 0) { auth.refresh(any()) }
    }

    @Test fun recoveryStorageFailureDoesNotGrantAccess() = runTest {
        coEvery { storage.read(RefreshToken::class) } returns tokens.refreshToken
        coEvery { auth.refresh(any()) } returns AuthResult.Success(tokens)
        coEvery { storage.save(any<AccessToken>(), AccessToken::class) } throws IllegalStateException()
        val session = SessionUseCase(auth, storage, cache)
        assertEquals(SessionState.LocalFailure, session.restore())
        assertNull(session.snapshot().accessToken)
        assertTrue(runCatching { session.requireAccess(session.snapshot()) }.isFailure)
        coVerify { storage.delete(RefreshToken::class) }
    }
}
