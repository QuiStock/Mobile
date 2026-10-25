package com.quistock.quistock.domain.model

import java.math.BigDecimal
import java.time.Instant

enum class StockFlow { HIGH, MEDIUM, LOW }

enum class AlertRisk { EXPIRY, STOCKOUT, OVERSTOCK }

data class AlertProduct(
    val id: String,
    val name: String,
    val category: String,
    val stock: BigDecimal,
    val expirationDays: Int?,
    val flow: StockFlow?,
    val analysisId: String? = null,
    val analysisDate: Instant? = null,
)

data class PriorityAlert(val product: AlertProduct, val risk: AlertRisk) {
    val hasPrimaryAction: Boolean get() = product.flow != StockFlow.MEDIUM
}

sealed interface AlertProductsResult {
    data class Success(val products: List<AlertProduct>, val invalidCount: Int) : AlertProductsResult
    data object Failure : AlertProductsResult
}

sealed interface PriorityAlertsResult {
    data class Success(val alerts: List<PriorityAlert>, val partial: Boolean) : PriorityAlertsResult
    data object Failure : PriorityAlertsResult
}
