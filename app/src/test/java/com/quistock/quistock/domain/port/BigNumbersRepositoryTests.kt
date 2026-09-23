package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.cache.CacheLoadState
import com.quistock.quistock.domain.cache.CachedValue
import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.time.Clock
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.Test
import kotlin.time.Instant

class BigNumbersRepositoryTests {
    private val remote = mockk<RemoteBigNumbersRepository>()
    private val local = mockk<CachedBigNumbersRepository>()
    private val clock = mockk<Clock>()
    private val logger = mockk<Logger>(relaxUnitFun = true)
    private val repository = BigNumbersRepository(remote, local, clock, logger)

    private val now = Instant.parse("2026-09-23T12:00:00Z")
    private val nextMidnight = Instant.parse("2026-09-24T00:00:00Z")
    private val numbers = BigNumbers(12, 3, 7)

    @Test
    fun `successful fetch saves fresh numbers and emits them`() = runTest {
        every { clock.now() } returns now
        every { clock.nextMidnight() } returns nextMidnight
        coEvery { local.read() } returns null
        coEvery { remote.fetch() } returns numbers
        coEvery { local.save(any(), any(), any()) } just Runs

        repository.load().first() shouldBe CacheLoadState.Data(numbers)

        coVerify(exactly = 1) { local.save(numbers, now, nextMidnight) }
    }

    @Test
    fun `network failure without cache emits unavailable`() = runTest {
        val failure = IOException("Network unavailable")
        every { clock.now() } returns now
        coEvery { local.read() } returns null
        coEvery { remote.fetch() } throws failure

        repository.load().first() shouldBe CacheLoadState.Unavailable

        verify(exactly = 1) { logger.warn("Failed to fetch remote", failure) }
        coVerify(exactly = 0) { local.save(any(), any(), any()) }
    }

    @Test
    fun `network failure with valid cache emits cached numbers`() = runTest {
        val failure = IOException("Network unavailable")
        every { clock.now() } returns now
        coEvery { local.read() } returns CachedValue(numbers, now, nextMidnight)
        coEvery { remote.fetch() } throws failure

        repository.load().first() shouldBe CacheLoadState.Data(numbers)

        verify(exactly = 1) { logger.warn("Failed to fetch remote", failure) }
    }

    @Test
    fun `network failure with expired cache marks numbers stale`() = runTest {
        every { clock.now() } returns now
        coEvery { local.read() } returns CachedValue(numbers, now, now)
        coEvery { remote.fetch() } throws IOException("Network unavailable")

        repository.load().first() shouldBe CacheLoadState.Data(numbers, staleWarning = true)
    }
}
