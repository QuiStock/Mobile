package com.quistock.quistock.presentation.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.model.RefreshResult
import com.quistock.quistock.domain.port.Clock
import com.quistock.quistock.domain.usecase.RefreshBigNumbersUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTests {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val useCase = mockk<RefreshBigNumbersUseCase>()
    private val clock = mockk<Clock>()
    private val viewModel = HomeViewModel(useCase, clock)
    private val now = Instant.parse("2026-09-23T12:00:00Z")
    private val today = Instant.parse("2026-09-23T00:00:00Z")
    private val earlier = Instant.parse("2026-09-22T12:00:00Z")
    private val later = Instant.parse("2026-09-24T12:00:00Z")

    private fun setClock() {
        every { clock.now() } returns now
        every { clock.timeZone } returns TimeZone.UTC
        every { clock.midnightOfDay(now) } returns today
        every { clock.midnightOfDay(earlier) } returns Instant.parse("2026-09-22T00:00:00Z")
        every { clock.midnightOfDay(later) } returns Instant.parse("2026-09-24T00:00:00Z")
    }

    @Test
    fun `load exposes loading without sample numbers and maps current counts including zero`() = runTest {
        val pending = CompletableDeferred<RefreshResult<BigNumbers>>()
        coEvery { useCase() } coAnswers { pending.await() }

        viewModel.load()
        viewModel.uiState.value shouldBe HomeUiState(loading = true)
        runCurrent()

        pending.complete(RefreshResult.UpToDate(BigNumbers(0, 3, 7, now)))
        advanceUntilIdle()

        viewModel.uiState.value shouldBe HomeUiState(numbers = HomeNumbers(0, 3, 7))
    }

    @Test
    fun `current cache has no warning or retry`() = runTest {
        coEvery { useCase() } returns RefreshResult.UpToDate(BigNumbers(1, 2, 3, now))

        viewModel.load()
        advanceUntilIdle()

        viewModel.uiState.value?.retryVisible shouldBe false
        viewModel.retry()
        coVerify(exactly = 1) { useCase() }
    }

    @Test
    fun `old cache shows its date and can be retried while preserving numbers`() = runTest {
        setClock()
        val cached = BigNumbers(1, 2, 3, earlier)
        val pending = CompletableDeferred<RefreshResult<BigNumbers>>()
        var calls = 0
        coEvery { useCase() } coAnswers {
            if (++calls == 1) RefreshResult.Stale(cached) else pending.await()
        }

        viewModel.load()
        advanceUntilIdle()
        viewModel.uiState.value shouldBe HomeUiState(
            numbers = HomeNumbers(1, 2, 3),
            warning = HomeWarning.STALE,
            dataDate = "22/09/2026",
        )

        viewModel.retry()
        viewModel.uiState.value shouldBe HomeUiState(
            numbers = HomeNumbers(1, 2, 3),
            loading = true,
            warning = HomeWarning.STALE,
            dataDate = "22/09/2026",
        )
        runCurrent()
        viewModel.retry()
        coVerify(exactly = 2) { useCase() }

        pending.complete(RefreshResult.Stale(cached))
        advanceUntilIdle()
        viewModel.uiState.value?.retryEnabled shouldBe true
        viewModel.uiState.value?.numbers shouldBe HomeNumbers(1, 2, 3)
    }

    @Test
    fun `future cache has clock warning and successful retry replaces values`() = runTest {
        setClock()
        coEvery { useCase() } returnsMany listOf(
            RefreshResult.Stale(BigNumbers(4, 5, 6, later)),
            RefreshResult.UpToDate(BigNumbers(7, 8, 9, now)),
        )

        viewModel.load()
        advanceUntilIdle()
        viewModel.uiState.value shouldBe HomeUiState(
            numbers = HomeNumbers(4, 5, 6),
            warning = HomeWarning.FUTURE_DATE,
            dataDate = "24/09/2026",
        )

        viewModel.retry()
        advanceUntilIdle()
        viewModel.uiState.value shouldBe HomeUiState(numbers = HomeNumbers(7, 8, 9))
        coVerify(exactly = 2) { useCase() }
    }

    @Test
    fun `failure without cache allows one retry at a time`() = runTest {
        val pending = CompletableDeferred<RefreshResult<BigNumbers>>()
        var calls = 0
        coEvery { useCase() } coAnswers {
            if (++calls == 1) RefreshResult.Failed else pending.await()
        }

        viewModel.load()
        advanceUntilIdle()
        viewModel.uiState.value shouldBe HomeUiState(error = true)

        viewModel.retry()
        viewModel.uiState.value?.retryEnabled shouldBe false
        runCurrent()
        viewModel.retry()
        coVerify(exactly = 2) { useCase() }

        pending.complete(RefreshResult.Failed)
        advanceUntilIdle()
        viewModel.uiState.value shouldBe HomeUiState(error = true)
    }

    @Test
    fun `each new view load requests the use case again`() = runTest {
        coEvery { useCase() } returns RefreshResult.UpToDate(BigNumbers(1, 2, 3, now))

        viewModel.load()
        advanceUntilIdle()
        viewModel.load()
        advanceUntilIdle()

        coVerify(exactly = 2) { useCase() }
    }
}
