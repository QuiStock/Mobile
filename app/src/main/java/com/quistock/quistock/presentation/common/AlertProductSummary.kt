package com.quistock.quistock.presentation.common

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.StockFlow

/** The two destination layouts expose the same product summary IDs. */
fun View.bindAlertProductSummary(product: AlertProduct) {
    findViewById<TextView>(R.id.alertProductName).apply {
        text = product.name
        contentDescription = product.name
    }
    findViewById<TextView>(R.id.alertProductQuantity).text = AlertProductPresentation.quantity(context, product)
    findViewById<TextView>(R.id.alertProductExpiry).text = AlertProductPresentation.expiry(context, product.expirationDays)
    findViewById<View>(R.id.alertProductExpiryExtra).isVisible = false
    findViewById<TextView>(R.id.alertProductFlow).setText(
        when (product.flow) {
            StockFlow.HIGH -> R.string.home_alerts_flow_high
            StockFlow.MEDIUM -> R.string.home_alerts_flow_medium
            StockFlow.LOW -> R.string.home_alerts_flow_low
            null -> R.string.home_alerts_flow_unavailable
        },
    )
}
