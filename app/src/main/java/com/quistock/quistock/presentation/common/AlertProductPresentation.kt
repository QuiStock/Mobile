package com.quistock.quistock.presentation.common

import android.content.Context
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.AlertProduct
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object AlertProductPresentation {
    fun quantity(context: Context, product: AlertProduct): String = context.getString(
        R.string.home_alerts_quantity,
        product.category,
        formatStock(product.stock),
    )

    internal fun formatStock(stock: BigDecimal, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = DECIMAL_PLACES
            roundingMode = RoundingMode.UNNECESSARY
        }.format(stock)

    fun expiry(context: Context, days: Int?): String = when (days) {
        null -> context.getString(R.string.home_alerts_expiry_unknown)
        0 -> context.getString(R.string.home_alerts_expiry_today)
        1 -> context.getString(R.string.home_alerts_expiry_tomorrow)
        else -> context.getString(R.string.home_alerts_expiry_days, days)
    }

    private const val DECIMAL_PLACES = 3
}
