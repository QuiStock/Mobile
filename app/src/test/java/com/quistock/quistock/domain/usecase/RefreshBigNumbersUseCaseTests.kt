package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.model.RefreshResult
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.Clock
import com.quistock.quistock.domain.port.ErrorReporter
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.RemoteBigNumbersRepository
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.Test
import kotlin.time.Instant

class RefreshBigNumbersUseCaseTests {
    private val remote = mockk<RemoteBigNumbersRepository>()
    private val local = mockk<CachedBigNumbersRepository>()
    private val clock = mockk<Clock>()
    private val logger = mockk<Logger>(relaxUnitFun = true)
    private val errorReporter = mockk<ErrorReporter>(relaxUnitFun = true)
    private val useCase = RefreshBigNumbersUseCase(remote, local, clock, logger, errorReporter)

    private val midnight = Instant.parse("2026-09-23T00:00:00Z")
    private val now = Instant.parse("2026-09-23T12:00:00Z")
    private val yesterday = Instant.parse("2026-09-22T12:00:00Z")
    private val fresh = BigNumbers(12, 3, 7, now)
    private val stale = BigNumbers(1, 2, 3, yesterday)

    private fun setClock() {
        every { clock.now() } returns now
        every { clock.midnightOfDay(now) } returns midnight
    }

    @Test
    fun `returns today's cache without fetching`() = runTest {
        setClock()
        coEvery { local.read() } returns fresh

        useCase() shouldBe RefreshResult.UpToDate(fresh)

        coVerify(exactly = 0) { remote.fetch() }
        coVerify(exactly = 0) { local.save(any()) }
    }

    @Test
    fun `midnight is part of the current cache day`() = runTest {
        setClock()
        val atMidnight = fresh.copy(createdAt = midnight)
        coEvery { local.read() } returns atMidnight

        useCase() shouldBe RefreshResult.UpToDate(atMidnight)
        coVerify(exactly = 0) { remote.fetch() }
    }

    @Test
    fun `fetches and saves when cache is missing`() = runTest {
        coEvery { local.read() } returns null
        coEvery { remote.fetch() } returns fresh
        coEvery { local.save(fresh) } just Runs

        useCase() shouldBe RefreshResult.UpToDate(fresh)
        coVerify(exactly = 1) { local.save(fresh) }
    }

    @Test
    fun `replaces stale cache after successful fetch`() = runTest {
        setClock()
        coEvery { local.read() } returns stale
        coEvery { remote.fetch() } returns fresh
        coEvery { local.save(fresh) } just Runs

        useCase() shouldBe RefreshResult.UpToDate(fresh)
        coVerify(exactly = 1) { local.save(fresh) }
    }

    @Test
    fun `returns stale cache when fetch fails`() = runTest {
        setClock()
        val failure = IOException("offline")
        coEvery { local.read() } returns stale
        coEvery { remote.fetch() } throws failure

        useCase() shouldBe RefreshResult.Stale(stale)
        coVerify(exactly = 0) { local.save(any()) }
        verify(exactly = 1) {
            logger.warn("Failed to fetch Big Numbers", failure, mapOf("operation" to "Big Numbers refresh"))
        }
    }

    @Test
    fun `fails when fetch fails without cache`() = runTest {
        val failure = IOException("offline")
        coEvery { local.read() } returns null
        coEvery { remote.fetch() } throws failure

        useCase() shouldBe RefreshResult.Failed
        coVerify(exactly = 0) { local.save(any()) }
    }

    @Test
    fun `returns fetched numbers when saving to cache fails`() = runTest {
        val failure = IllegalStateException("disk full")
        coEvery { local.read() } returns null
        coEvery { remote.fetch() } returns fresh
        coEvery { local.save(fresh) } throws failure

        useCase() shouldBe RefreshResult.UpToDate(fresh)

        verify(exactly = 1) { errorReporter.record(failure, mapOf("operation" to "Big Numbers refresh")) }
    }

    @Test
    fun `does not reuse cache from a previous invocation`() = runTest {
        setClock()
        coEvery { local.read() } returnsMany listOf(stale, null)
        coEvery { remote.fetch() } throws IOException("offline")

        useCase() shouldBe RefreshResult.Stale(stale)
        useCase() shouldBe RefreshResult.Failed
    }
}
