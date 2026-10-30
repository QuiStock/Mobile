package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.AlertRisk
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.PriorityAlertsResult
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class LoadPriorityAlertsUseCaseTests {
    private val repository = mockk<PriorityAlertsRepository>()
    private val load = LoadPriorityAlertsUseCase(repository)

    @Test
    fun `risk rather than speed selects alerts including medium promotion`() = runTest {
        val products = listOf(
            product("none", StockFlow.HIGH, emptySet()),
            product("expiry", StockFlow.MEDIUM, setOf(CreationRisk.NEAR_EXPIRY)),
            product("stockout", StockFlow.LOW, setOf(CreationRisk.STOCKOUT)),
            product("expiration", StockFlow.HIGH, setOf(CreationRisk.EXPIRATION)),
            product("expired", StockFlow.HIGH, setOf(CreationRisk.EXPIRED)),
            product("expired-stockout", StockFlow.MEDIUM, setOf(CreationRisk.EXPIRED, CreationRisk.STOCKOUT)),
        )
        coEvery { repository.read(17, "store") } returns AlertProductsResult.Success(products, 0)
        val result = load(17, "store") as PriorityAlertsResult.Success
        assertEquals(
            listOf("expiry", "expired-stockout", "stockout", "expiration"),
            result.alerts.map { it.product.id },
        )
        assertTrue(result.alerts.all { it.hasPrimaryAction })
    }

    @Test
    fun `inventory risks stay visible without sales classification while creation stays blocked`() = runTest {
        val products = listOf(
            product("expiry", StockFlow.MEDIUM, setOf(CreationRisk.NEAR_EXPIRY)),
            product("stockout", StockFlow.MEDIUM, setOf(CreationRisk.STOCKOUT)),
            product("expiration", StockFlow.MEDIUM, setOf(CreationRisk.EXPIRATION)),
            product("none", StockFlow.MEDIUM, emptySet()),
        ).map { it.copy(flow = null) }
        coEvery { repository.read(17, "store") } returns AlertProductsResult.Success(products, 0)
        val result = load(17, "store") as PriorityAlertsResult.Success
        assertEquals(listOf("expiry", "stockout", "expiration"), result.alerts.map { it.product.id })
        assertEquals(listOf(AlertRisk.EXPIRY, AlertRisk.STOCKOUT, AlertRisk.OVERSTOCK), result.alerts.map { it.risk })
        assertTrue(result.alerts.all { it.product.flow == null })
        assertFalse(result.alerts.any { it.hasPrimaryAction })
    }

    @Test
    fun `valid empty partial and failure remain distinguishable`() = runTest {
        coEvery { repository.read(17, "store") } returns AlertProductsResult.Success(emptyList(), 1)
        val result = load(17, "store") as PriorityAlertsResult.Success
        assertTrue(result.alerts.isEmpty())
        assertTrue(result.partial)
        coEvery { repository.read(17, "store") } returns AlertProductsResult.Failure
        assertEquals(PriorityAlertsResult.Failure, load(17, "store"))
    }

    private fun product(id: String, flow: StockFlow, risks: Set<CreationRisk>) =
        AlertProduct(id, id, "Category", BigDecimal.ONE, null, flow, "analysis", risks = risks)
}
