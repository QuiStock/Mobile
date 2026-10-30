package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.CreationRisk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CurrentAnalysisMapperTests {
    private val product = AlertProduct("product", "Product", "Category", BigDecimal("2.375"), null, null)
    private val row = """
        {"id":"flow","product_id":"product","store_id":17,"classifier":"SALES_PERCENTILES_V1",
        "is_current":true,"status":"ANALYZED","flow_type":"MEDIUM","analysis_date":"2026-10-29T12:00:00Z",
        "risks":["STOCKOUT","EXPIRATION"]}
    """.trimIndent()

    @Test
    fun `current medium analysis may support both types even without product expiry`() {
        val mapped = CurrentAnalysisMapper.select(product, Json.parseToJsonElement("[$row]").jsonArray, 17)
        assertEquals(setOf(ActionType.ORDER, ActionType.PROMOTION), mapped.actionTypes())
        assertEquals(product.stock, mapped.stock)
    }

    @Test
    fun `missing current analysis does not invent risks or creation eligibility`() {
        val mapped = CurrentAnalysisMapper.select(product, Json.parseToJsonElement("[]").jsonArray, 17)
        assertTrue(mapped.risks.isEmpty())
        assertTrue(mapped.actionTypes().isEmpty())
    }

    @Test
    fun `insufficient analysis preserves current risks identity and date without enabling creation`() {
        val insufficient = row.replace("ANALYZED", "INSUFFICIENT_DATA").replace("\"MEDIUM\"", "null")
        val mapped = CurrentAnalysisMapper.select(product, Json.parseToJsonElement("[$insufficient]").jsonArray, 17)
        assertEquals(setOf(CreationRisk.STOCKOUT, CreationRisk.EXPIRATION), mapped.risks)
        assertEquals("flow", mapped.analysisId)
        assertEquals(Instant.parse("2026-10-29T12:00:00Z"), mapped.analysisDate)
        assertEquals(product.stock, mapped.stock)
        assertNull(mapped.flow)
        assertTrue(mapped.actionTypes().isEmpty())
    }

    @Test
    fun `legacy foreign store noncurrent unknown risk and duplicate rows are incompatible`() {
        val invalid = listOf(
            row.replace("SALES_PERCENTILES_V1", "RULE_ENGINE"),
            row.replace("true", "false"),
            row.replace(":17", ":18"),
            row.replace("STOCKOUT", "UNKNOWN"),
            "$row,$row",
            row.replace("ANALYZED", "INSUFFICIENT_DATA"),
            row.replace("ANALYZED", "UNKNOWN"),
        )
        invalid.forEach { body ->
            assertTrue(
                runCatching {
                    CurrentAnalysisMapper.select(product, Json.parseToJsonElement("[$body]").jsonArray, 17)
                }.isFailure,
            )
        }
    }
}
