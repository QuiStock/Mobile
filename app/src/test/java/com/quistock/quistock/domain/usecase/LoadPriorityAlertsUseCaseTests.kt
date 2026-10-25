package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.AlertRisk
import com.quistock.quistock.domain.model.PriorityAlertsResult
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class LoadPriorityAlertsUseCaseTests {
    @Test
    fun `expiry includes today and seven days with any analysis but excludes expired and unanalyzed`() = runTest {
        val products = listOf(
            product("expired", -1, StockFlow.HIGH),
            product("today", 0, StockFlow.MEDIUM),
            product("seven", 7, StockFlow.MEDIUM),
            product("eight", 8, StockFlow.MEDIUM),
            product("unanalyzed", 1, null),
        )

        val result = load(products)

        assertEquals(listOf("today", "seven"), result.alerts.map { it.product.id })
        assertTrue(result.alerts.all { it.risk == AlertRisk.EXPIRY })
    }

    @Test
    fun `expiry overrides flow and stockout precedes alphabetically sorted excess`() = runTest {
        val result = load(
            listOf(
                product("z", null, StockFlow.LOW),
                product("a", 10, StockFlow.LOW),
                product("rupture", null, StockFlow.HIGH),
                product("expiry", 2, StockFlow.HIGH),
            ),
        )

        assertEquals(listOf("expiry", "rupture", "a", "z"), result.alerts.map { it.product.id })
        assertEquals(AlertRisk.EXPIRY, result.alerts.first().risk)
    }

    @Test
    fun `different consolidated IDs with same name remain separate and empty selection retains partial warning`() =
        runTest {
            val lots = listOf(product("one", 1, StockFlow.LOW), product("two", 1, StockFlow.LOW))
                .map { it.copy(name = "Same product") }

            assertEquals(2, load(lots).alerts.size)
            val partial = load(listOf(product("adequate", null, StockFlow.MEDIUM)), invalidCount = 1)
            assertTrue(partial.alerts.isEmpty())
            assertTrue(partial.partial)
        }

    @Test
    fun `repository failure remains a failure instead of empty alerts`() = runTest {
        val repository = object : PriorityAlertsRepository {
            override suspend fun read(storeId: Long, storeCode: String?) = AlertProductsResult.Failure
        }

        assertEquals(PriorityAlertsResult.Failure, LoadPriorityAlertsUseCase(repository)(17, "store"))
    }

    private suspend fun load(products: List<AlertProduct>, invalidCount: Int = 0): PriorityAlertsResult.Success {
        val repository = object : PriorityAlertsRepository {
            override suspend fun read(storeId: Long, storeCode: String?): AlertProductsResult {
                assertEquals(17L, storeId)
                assertEquals("store", storeCode)
                return AlertProductsResult.Success(products, invalidCount)
            }
        }
        return LoadPriorityAlertsUseCase(repository)(17, "store") as PriorityAlertsResult.Success
    }

    private fun product(id: String, days: Int?, flow: StockFlow?) =
        AlertProduct(id, id, "Category", BigDecimal("5"), days, flow)
}
