package com.quistock.quistock.data.remote.mock

import com.quistock.quistock.domain.model.BigNumbers
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.Test
import kotlin.time.Instant

class MockRemoteBigNumbersRepositoryTests {
    @Test
    fun `fetch returns sample numbers and subsequent changes`() = runTest {
        val repository = MockRemoteBigNumbersRepository()

        repository.fetch().let {
            Triple(it.nearExpirationProductCount, it.criticalAnalyzedFlowCount, it.activeActionCount)
        } shouldBe
            Triple(12, 3, 7)

        val updated = BigNumbers(4, 2, 9, Instant.parse("2026-09-23T12:00:00Z"))
        repository.numbers = updated
        repository.fetch().let {
            Triple(it.nearExpirationProductCount, it.criticalAnalyzedFlowCount, it.activeActionCount)
        } shouldBe
            Triple(4, 2, 9)
    }

    @Test
    fun `fetch can simulate a remote failure`() = runTest {
        val repository = MockRemoteBigNumbersRepository()
        val failure = IOException("Network unavailable")
        repository.failure = failure

        runCatching { repository.fetch() }.exceptionOrNull() shouldBe failure
    }
}
