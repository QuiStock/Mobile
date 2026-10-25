package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.StockFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class HistoricalAnalysisMapperTests {
    private val product = AlertProduct("product:store", "Fixture", "Category", BigDecimal("2.375"), 2, null)

    @Test
    fun `selection compares instants with different offsets and preserves current product validity`() {
        val result = select(
            flow("999", "2026-10-20T12:00:00+02:00"),
            flow("1", "2026-10-20T08:30:00-03:00", type = "LOW"),
            flow("2", "2026-10-19T00:00:00Z"),
        )

        assertEquals("1", result?.analysisId)
        assertEquals(Instant.parse("2026-10-20T11:30:00Z"), result?.analysisDate)
        assertEquals(StockFlow.LOW, result?.flow)
        assertEquals(2, result?.expirationDays)
    }

    @Test
    fun `ambiguous latest instant is not resolved by greatest ID`() {
        assertNull(select(flow("1", "2026-10-20T10:00:00Z"), flow("999", "2026-10-20T10:00:00Z")))
    }

    @Test
    fun `unreliable date prevents silent fallback to another analysis`() {
        listOf("null", "\"invalid\"", "\"2026-10-20T10:00:00\"").forEach { date ->
            val invalid = """{"id":"unknown","product_id":"product:store","flow_type":"HIGH","status":"ANALYZED","analysis_date":$date}"""
            assertNull(select(flow("valid", "2026-10-20T10:00:00Z"), invalid))
        }
    }

    @Test
    fun `empty history means no analysis while a single old dated observation is allowed`() {
        assertNull(select()?.flow)
        assertEquals("old", select(flow("old", "2020-01-01T00:00:00Z"))?.analysisId)
    }

    @Test
    fun `foreign product unknown type and conflicting identities are rejected`() {
        assertNull(select(flow("id", "2026-10-20T10:00:00Z", productId = "another:store")))
        assertNull(select(flow("id", "2026-10-20T10:00:00Z", type = "UNKNOWN")))
        assertNull(select(flow("id", "2026-10-20T10:00:00Z"), flow("id", "2026-10-21T10:00:00Z")))
    }

    private fun select(vararg rows: String): AlertProduct? =
        HistoricalAnalysisMapper.select(product, Json.parseToJsonElement(rows.joinToString(",", "[", "]")).jsonArray)

    private fun flow(id: String, date: String, type: String = "HIGH", productId: String = "product:store") =
        """{"id":"$id","product_id":"$productId","flow_type":"$type","status":"ANALYZED","analysis_date":"$date","expiration_days":-10}"""
}
