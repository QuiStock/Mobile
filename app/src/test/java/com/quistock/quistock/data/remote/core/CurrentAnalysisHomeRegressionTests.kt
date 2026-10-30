package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.AlertRisk
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.PriorityAlertsResult
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.usecase.LoadPriorityAlertsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class CurrentAnalysisHomeRegressionTests {
    private val api = mockk<PriorityAlertsApi>()
    private val repository = RetrofitPriorityAlertsRepository(api, mockk<Logger>(relaxed = true))

    @Test
    fun `insufficient current expiration risk reaches Home without allowing creation`() = runTest {
        coEvery { api.branches() } returns json("""[{"id":"store","store_id":17,"name":"Fixture store"}]""")
        coEvery { api.products("Fixture store") } returns json(
            """[{"id":"valid","name":"Fixture product","category":"Category",
                "current_stock":80,"expiration_days":44,"branch":"Fixture store"}]""",
        )
        coEvery { api.current(17) } returns json(
            """[{"id":"analysis","product_id":"valid","flow_type":null,"status":"INSUFFICIENT_DATA",
                "analysis_date":"2026-10-30T19:22:04.864871Z","store_id":17,"is_current":true,
                "classifier":"SALES_PERCENTILES_V1","risks":["EXPIRATION"]}]""",
        )
        val result = LoadPriorityAlertsUseCase(repository)(17, "store") as PriorityAlertsResult.Success
        val alert = result.alerts.single()
        assertEquals(AlertRisk.OVERSTOCK, alert.risk)
        assertEquals(setOf(CreationRisk.EXPIRATION), alert.product.risks)
        assertEquals("analysis", alert.product.analysisId)
        assertNull(alert.product.flow)
        assertFalse(alert.hasPrimaryAction)
        assertFalse(result.partial)
    }

    private fun json(value: String) = Response.success(Json.parseToJsonElement(value).jsonArray)
}
