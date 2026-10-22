package com.quistock.quistock.presentation.common

import android.os.Bundle
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.StockFlow

/** Only product context crosses navigation; no credentials or profile payloads. */
object AlertProductNavigation {
    private const val PRODUCT_ID = "priority_alert_product_id"
    private const val PRODUCT_NAME = "priority_alert_product_name"
    private const val CATEGORY = "priority_alert_category"
    private const val STOCK = "priority_alert_stock"
    private const val EXPIRATION = "priority_alert_expiration_days"
    private const val FLOW = "priority_alert_flow"

    fun arguments(product: AlertProduct) = Bundle().apply {
        putString(PRODUCT_ID, product.id)
        putString(PRODUCT_NAME, product.name)
        putString(CATEGORY, product.category)
        putLong(STOCK, product.stock)
        product.expirationDays?.let { putInt(EXPIRATION, it) }
        putString(FLOW, product.flow?.name)
    }

    fun product(arguments: Bundle?): AlertProduct? {
        val args = arguments ?: return null
        val id = args.getString(PRODUCT_ID)?.takeIf { it.isNotBlank() } ?: return null
        val name = args.getString(PRODUCT_NAME)?.takeIf { it.isNotBlank() } ?: return null
        val category = args.getString(CATEGORY)?.takeIf { it.isNotBlank() } ?: return null
        if (!args.containsKey(STOCK)) return null
        return AlertProduct(
            id, name, category, args.getLong(STOCK),
            if (args.containsKey(EXPIRATION)) args.getInt(EXPIRATION) else null,
            StockFlow.entries.find { it.name == args.getString(FLOW) },
        )
    }
}
