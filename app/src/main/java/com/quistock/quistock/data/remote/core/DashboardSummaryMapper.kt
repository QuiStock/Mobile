package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.HomeSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object DashboardSummaryMapper {
    fun parse(body: String): HomeSummary {
        val fields = Json.parseToJsonElement(body).jsonObject
        fun count(name: String): Long {
            val value = requireNotNull(fields[name]).jsonPrimitive
            require(!value.isString)
            return requireNotNull(value.content.toLongOrNull()).also { require(it >= 0) }
        }
        return HomeSummary(
            count("near_expiry_products"),
            count("stockout_products"),
            count("overstock_products"),
            count("active_actions"),
        )
    }
}
