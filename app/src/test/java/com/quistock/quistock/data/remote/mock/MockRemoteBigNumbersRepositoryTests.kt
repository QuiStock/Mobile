package com.quistock.quistock.data.remote.mock

import com.quistock.quistock.domain.model.BigNumbers
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.Test

class MockRemoteBigNumbersRepositoryTests {
    @Test
    fun `fetch returns sample numbers and subsequent changes`() = runTest {
        val repository = MockRemoteBigNumbersRepository()

        repository.fetch() shouldBe BigNumbers(12, 3, 7)

        val updated = BigNumbers(4, 2, 9)
        repository.numbers = updated
        repository.fetch() shouldBe updated
    }

    @Test
    fun `fetch can simulate a remote failure`() = runTest {
        val repository = MockRemoteBigNumbersRepository()
        val failure = IOException("Network unavailable")
        repository.failure = failure

        runCatching { repository.fetch() }.exceptionOrNull() shouldBe failure
    }
}
