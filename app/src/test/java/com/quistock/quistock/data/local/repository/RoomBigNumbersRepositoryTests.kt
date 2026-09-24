package com.quistock.quistock.data.local.repository

import com.quistock.quistock.data.local.dao.BigNumbersDao
import com.quistock.quistock.data.local.entity.BigNumbersEntity
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
    private val createdAt = Instant.parse("2026-09-23T12:00:00Z")
    private val numbers = BigNumbers(12, 3, 7, createdAt)

    @Test
    fun `read returns null when database is empty`() = runTest {
        coEvery { dao.getLatest() } returns null

        repository.read() shouldBe null
    }

    @Test
    fun `read maps stored counts and timestamps`() = runTest {
        coEvery { dao.getLatest() } returns entity(id = 4)

        val result = repository.read()

        result shouldBe numbers
    }

    @Test
    fun `save replaces previous entry with mapped fresh value`() = runTest {
        repository.save(numbers)

        coVerify(exactly = 1) {
            dao.replaceLatest(
                match {
                    it.createdAt == createdAt &&
                        it.nearExpirationProductCount == 12 &&
                        it.criticalAnalyzedFlowCount == 3 &&
                        it.activeActionCount == 7
                },
            )
        }
    }

    private fun entity(id: Long) = BigNumbersEntity(
        id = id,
        createdAt = createdAt,
        nearExpirationProductCount = 12,
        criticalAnalyzedFlowCount = 3,
        activeActionCount = 7,
    )
}
