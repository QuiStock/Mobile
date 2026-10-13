package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.model.RefreshResult
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.Clock
import com.quistock.quistock.domain.port.ErrorReporter
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.RemoteBigNumbersRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class SessionBigNumbersTests {
    private val remote = mockk<RemoteBigNumbersRepository>()
    private val cache = mockk<CachedBigNumbersRepository>(relaxed = true)
    private val clock = mockk<Clock>()
    private val logger = mockk<Logger>(relaxed = true)
    private val reporter = mockk<ErrorReporter>(relaxed = true)
    private val auth = mockk<AuthRepository>(relaxed = true).also {
        coEvery { it.login(any(), any(), any()) } returns com.quistock.quistock.domain.model.AuthResult.Success
        io.mockk.every { it.hasAccess(any()) } returns true
    }
    private val session = SessionUseCase(auth, cache)
    private val useCase = RefreshBigNumbersUseCase(remote, cache, clock, logger, reporter, session)
    private val fresh = BigNumbers(1, 2, 3, Instant.parse("2026-10-01T12:00:00Z"))

    @Test fun lateRemoteResultDoesNotRepopulateExpiredCache() = runTest {
        coEvery { cache.read() } returns null
        val pending = CompletableDeferred<BigNumbers>()
        coEvery { remote.fetch() } coAnswers { pending.await() }
        session.login("synthetic@example.com", "synthetic")
        val expected = session.snapshot()
        val operation = async { runCatching { useCase() } }
        yield()
        session.expire(expected)
        session.login("synthetic@example.com", "synthetic")
        pending.complete(fresh)
        assertTrue(operation.await().exceptionOrNull() is SessionException)
        coVerify(exactly = 0) { cache.save(any()) }
    }

    @Test fun transientSessionExceptionUsesAvailableCacheAndDefinitiveRejectionDoesNot() = runTest {
        coEvery { cache.read() } returns null
        session.login("synthetic@example.com", "synthetic")
        for (reason in listOf(SessionFailure.NETWORK, SessionFailure.TIMEOUT, SessionFailure.SERVER)) {
            coEvery { remote.fetch() } throws SessionException(reason)
            assertEquals(RefreshResult.Failed, useCase())
        }
        coEvery { remote.fetch() } throws SessionException()
        assertTrue(runCatching { useCase() }.exceptionOrNull() is SessionException)
        coVerify(exactly = 0) { cache.save(any()) }
    }
}
