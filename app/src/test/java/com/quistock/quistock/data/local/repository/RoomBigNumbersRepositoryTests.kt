package com.quistock.quistock.data.local.repository

import com.quistock.quistock.data.local.dao.BigNumbersDao
import com.quistock.quistock.data.local.entity.BigNumbersEntity
import com.quistock.quistock.domain.cache.CachedValue
import com.quistock.quistock.domain.model.BigNumbers
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

class RoomBigNumbersRepositoryTests {
    private val dao = mockk<BigNumbersDao>(relaxed = true)
    private val repository = RoomBigNumbersRepository(dao)
    private val savedAt = Instant.parse("2026-09-23T12:00:00Z")
    private val expiresAt = Instant.parse("2026-09-24T00:00:00Z")
    private val numbers = BigNumbers(12, 3, 7)

    @Test
    fun `read returns null when database is empty`() = runTest {
        coEvery { dao.getLatest() } returns null

        repository.read() shouldBe null
    }

    @Test
    fun `read maps stored counts and timestamps`() = runTest {
        coEvery { dao.getLatest() } returns entity(id = 4)

        val result = repository.read()

        result?.value shouldBe numbers
        result?.savedAt shouldBe savedAt
        result?.expiresAt shouldBe expiresAt
    }

    @Test
    fun `save replaces previous entry with mapped fresh value`() = runTest {
        val previous = entity(id = 4)
        coEvery { dao.getLatest() } returns previous

        repository.save(CachedValue(numbers, savedAt, expiresAt))

        coVerify(exactly = 1) { dao.delete(previous) }
        coVerify(exactly = 1) {
            dao.insert(
                match {
                    it.savedAt == savedAt &&
                        it.expiresAt == expiresAt &&
                        it.nearExpirationProductCount == 12 &&
                        it.criticalAnalyzedFlowCount == 3 &&
                        it.activeActionCount == 7
                },
            )
        }
    }

    private fun entity(id: Long) = BigNumbersEntity(
        id = id,
        savedAt = savedAt,
        expiresAt = expiresAt,
        nearExpirationProductCount = 12,
        criticalAnalyzedFlowCount = 3,
        activeActionCount = 7,
    )
}
