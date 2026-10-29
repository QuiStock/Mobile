package com.quistock.quistock.data.remote.core

import com.quistock.quistock.data.remote.core.InterferenceMapper.text
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.StockFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

internal object CurrentAnalysisMapper {
    fun select(product: AlertProduct, rows: JsonArray, storeId: Long): AlertProduct {
        val matches = rows.filterIsInstance<JsonObject>().filter { it.text("product_id") == product.id }
        require(matches.size <= 1)
        val row = matches.singleOrNull() ?: return product
        require(row["store_id"]?.jsonPrimitive?.longOrNull == storeId)
        require(row["is_current"]?.jsonPrimitive?.booleanOrNull == true)
        require(row.text("classifier") == "SALES_PERCENTILES_V1")
        return if (row.text("status") == "INSUFFICIENT_DATA") {
            product
        } else {
            analyzed(product, row)
        }
    }

    private fun analyzed(product: AlertProduct, row: JsonObject): AlertProduct {
        require(row.text("status") == "ANALYZED")
        val risks = requireNotNull(row["risks"] as? JsonArray).map {
            CreationRisk.valueOf(it.jsonPrimitive.content)
        }.toSet()
        return product.copy(
            flow = StockFlow.valueOf(requireNotNull(row.text("flow_type"))),
            analysisId = requireNotNull(row.text("id")),
            analysisDate = Instant.parse(requireNotNull(row.text("analysis_date"))),
            risks = risks,
        )
    }
}
