package com.quistock.quistock.presentation.common

import android.content.Context
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.AlertProduct
import java.text.NumberFormat

object AlertProductPresentation {
    fun quantity(context: Context, product: AlertProduct): String = if (product.stock == 1L) {
        context.getString(R.string.home_alerts_quantity_single, product.category)
    } else {
        context.getString(
            R.string.home_alerts_quantity,
            product.category,
            NumberFormat.getIntegerInstance().format(product.stock),
        )
    }

    fun expiry(context: Context, days: Int?): String = when (days) {
        null -> context.getString(R.string.home_alerts_expiry_unknown)
        0 -> context.getString(R.string.home_alerts_expiry_today)
        1 -> context.getString(R.string.home_alerts_expiry_tomorrow)
        else -> context.getString(R.string.home_alerts_expiry_days, days)
    }
}
