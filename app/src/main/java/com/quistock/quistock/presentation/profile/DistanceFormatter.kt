package com.quistock.quistock.presentation.profile

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** Formats a distance in Brazilian Portuguese: "350 m", "2,3 km", "120 km". */
object DistanceFormatter {
    private val locale = Locale.forLanguageTag("pt-BR")

    fun format(meters: Double): String {
        val safe = meters.coerceAtLeast(0.0)
        val roundedMeters = (safe / METER_STEP).roundToLong() * METER_STEP
        return when {
            roundedMeters < METERS_PER_KM -> "$roundedMeters m"

            safe < WHOLE_KM_FROM -> NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }.format(safe / METERS_PER_KM) + " km"

            else -> NumberFormat.getIntegerInstance(locale).format((safe / METERS_PER_KM).roundToLong()) + " km"
        }
    }

    private const val METERS_PER_KM = 1_000L
    private const val METER_STEP = 10L
    private const val WHOLE_KM_FROM = 100_000.0
}
