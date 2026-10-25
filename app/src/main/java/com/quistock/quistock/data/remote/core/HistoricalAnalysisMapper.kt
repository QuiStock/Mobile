package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.StockFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Chooses a historical observation, never asserting that it represents the current stock. */
internal object HistoricalAnalysisMapper {
    fun select(product: AlertProduct, rows: JsonArray): AlertProduct? = if (rows.isEmpty()) {
        product
    } else {
        val parsed = rows.map { parse(it, product.id) }
        if (parsed.any { it == null }) null else latest(product, parsed.filterNotNull().distinct())
    }

    private fun latest(product: AlertProduct, analyses: List<Analysis>): AlertProduct? {
        val conflictingIds = analyses.groupBy { it.id }.values.any { it.size > 1 }
        if (conflictingIds) return null
        val latestDate = analyses.maxOf { it.date }
        return analyses.filter { it.date == latestDate }.singleOrNull()?.let {
            product.copy(flow = it.type, analysisId = it.id, analysisDate = it.date)
        }
    }

    private fun parse(row: JsonElement, productId: String): Analysis? {
        val value = row as? JsonObject ?: return null
        val id = value.text("id")
        val type = StockFlow.entries.find { it.name == value.text("flow_type") }
        val date = parseDate(value.text("analysis_date"))
        val matches = value.text("product_id") == productId && value.text("status") == "ANALYZED"
        val analysis = if (id != null && type != null && date != null) Analysis(id, type, date) else null
        return analysis?.takeIf { matches }
    }

    private fun parseDate(text: String?): Instant? = try {
        text?.let { OffsetDateTime.parse(it).toInstant() }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private data class Analysis(val id: String, val type: StockFlow, val date: Instant)
}
