package com.quistock.quistock.presentation.common

import android.content.Context
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.AlertProduct
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

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

    internal fun formatAverageDailySales(sales7d: BigDecimal, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = DECIMAL_PLACES
            roundingMode = RoundingMode.UNNECESSARY
        }.format(sales7d.divide(BigDecimal(DAYS_IN_WEEK), DECIMAL_PLACES, RoundingMode.HALF_UP))

    fun skuCategory(context: Context, sku: String?, category: String): String = context.getString(
        R.string.product_control_sku,
        sku ?: context.getString(R.string.product_control_unavailable),
        category,
    )

    fun expiry(context: Context, days: Int?): String = when {
        days == null -> context.getString(R.string.home_alerts_expiry_unknown)
        days == 0 -> context.getString(R.string.home_alerts_expiry_today)
        days == 1 -> context.getString(R.string.home_alerts_expiry_tomorrow)
        days == -1 -> context.getString(R.string.home_alerts_expiry_past_day)
        days < 0 -> context.getString(R.string.home_alerts_expiry_past_days, days.toLong().absoluteValue)
        else -> context.getString(R.string.home_alerts_expiry_days, days)
    }

    fun expiryDays(context: Context, days: Int?): String = when {
        days == null -> context.getString(R.string.product_control_unavailable)

        days == 0 -> context.getString(R.string.product_control_expiration_days_today)

        days == -1 -> context.getString(R.string.product_control_expiration_days_past_day)

        days < 0 -> context.getString(
            R.string.product_control_expiration_days_past,
            days.toLong().absoluteValue,
        )

        days == 1 -> context.getString(R.string.product_control_expiration_days_single)

        else -> context.getString(R.string.product_control_expiration_days_value, days)
    }

    private const val DECIMAL_PLACES = 3
    private const val DAYS_IN_WEEK = 7
}
