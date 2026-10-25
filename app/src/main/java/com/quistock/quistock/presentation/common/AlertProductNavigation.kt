package com.quistock.quistock.presentation.common

import android.os.Bundle
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.StockFlow
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeParseException

/** Only product context crosses navigation; no credentials or profile payloads. */
object AlertProductNavigation {
    private const val PRODUCT_ID = "priority_alert_product_id"
    private const val PRODUCT_NAME = "priority_alert_product_name"
    private const val CATEGORY = "priority_alert_category"
    private const val STOCK = "priority_alert_stock"
    private const val EXPIRATION = "priority_alert_expiration_days"
    private const val FLOW = "priority_alert_flow"
    private const val ANALYSIS_ID = "priority_alert_analysis_id"
    private const val ANALYSIS_DATE = "priority_alert_analysis_date"
    private const val DECIMAL_PLACES = 3

    fun arguments(product: AlertProduct) = Bundle().apply {
        putString(PRODUCT_ID, product.id)
        putString(PRODUCT_NAME, product.name)
        putString(CATEGORY, product.category)
        putString(STOCK, product.stock.toPlainString())
        product.expirationDays?.let { putInt(EXPIRATION, it) }
        putString(FLOW, product.flow?.name)
        putString(ANALYSIS_ID, product.analysisId)
        putString(ANALYSIS_DATE, product.analysisDate?.toString())
    }

    fun product(arguments: Bundle?): AlertProduct? {
        val args = arguments ?: return null
        val stock = args.quantity()
        return if (args.hasProductIdentity() && stock != null) {
            AlertProduct(
                requireNotNull(args.getString(PRODUCT_ID)),
                requireNotNull(args.getString(PRODUCT_NAME)),
                requireNotNull(args.getString(CATEGORY)),
                stock,
                if (args.containsKey(EXPIRATION)) args.getInt(EXPIRATION) else null,
                StockFlow.entries.find { it.name == args.getString(FLOW) },
                args.getString(ANALYSIS_ID),
                args.analysisDate(),
            )
        } else {
            null
        }
    }

    private fun Bundle.hasProductIdentity(): Boolean =
        listOf(PRODUCT_ID, PRODUCT_NAME, CATEGORY).all { !getString(it).isNullOrBlank() }

    private fun Bundle.quantity(): BigDecimal? = getString(STOCK)?.toBigDecimalOrNull()?.stripTrailingZeros()
        ?.takeIf { it.signum() >= 0 && it.scale() <= DECIMAL_PLACES }

    private fun Bundle.analysisDate(): Instant? = try {
        getString(ANALYSIS_DATE)?.let { Instant.parse(it) }
    } catch (_: DateTimeParseException) {
        null
    }
}
