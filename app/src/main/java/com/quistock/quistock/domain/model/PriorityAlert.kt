package com.quistock.quistock.domain.model

enum class StockFlow { HIGH, MEDIUM, LOW }

enum class AlertRisk { EXPIRY, STOCKOUT, OVERSTOCK }

data class AlertProduct(
    val id: String,
    val name: String,
    val category: String,
    val stock: Long,
    val expirationDays: Int?,
    val flow: StockFlow?,
)

data class PriorityAlert(val product: AlertProduct, val risk: AlertRisk)

sealed interface AlertProductsResult {
    data class Success(val products: List<AlertProduct>, val invalidCount: Int) : AlertProductsResult
    data object Failure : AlertProductsResult
}

sealed interface PriorityAlertsResult {
    data class Success(val alerts: List<PriorityAlert>, val partial: Boolean) : PriorityAlertsResult
    data object Failure : PriorityAlertsResult
}
