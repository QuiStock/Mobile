package com.quistock.quistock.presentation.common

import android.content.Context
import com.quistock.quistock.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class AlertProductPresentationTests {
    private val context = mockk<Context>()

    @Test
    fun `quantities preserve fractions without trailing zeros or unit suffix`() {
        val locale = Locale.forLanguageTag("pt-BR")
        mapOf("5" to "5", "5.000" to "5", "2.375" to "2,375", "0.010" to "0,01").forEach { (value, expected) ->
            assertEquals(expected, AlertProductPresentation.formatStock(BigDecimal(value), locale))
        }
    }

    @Test
    fun `weekly sales are divided by seven and rounded half up to three places`() {
        val locale = Locale.forLanguageTag("pt-BR")
        assertEquals("2,071", AlertProductPresentation.formatAverageDailySales(BigDecimal("14.500"), locale))
        assertEquals("0,143", AlertProductPresentation.formatAverageDailySales(BigDecimal.ONE, locale))
        assertEquals("1", AlertProductPresentation.formatAverageDailySales(BigDecimal("7"), locale))
    }

    @Test
    fun `missing SKU keeps the product category in its subtitle`() {
        every { context.getString(R.string.product_control_unavailable) } returns "Indisponível"
        every {
            context.getString(R.string.product_control_sku, "Indisponível", "Laticínios")
        } returns "SKU Indisponível · Laticínios"

        assertEquals("SKU Indisponível · Laticínios", AlertProductPresentation.skuCategory(context, null, "Laticínios"))
    }

    @Test
    fun `expiry labels past dates explicitly and handles zero and singular days`() {
        every { context.getString(R.string.home_alerts_expiry_today) } returns "Vence hoje"
        every { context.getString(R.string.home_alerts_expiry_tomorrow) } returns "Vence em 1 dia"
        every { context.getString(R.string.home_alerts_expiry_past_day) } returns "Vencido há 1 dia"
        every { context.getString(R.string.home_alerts_expiry_past_days, 8L) } returns "Vencido há 8 dias"

        assertEquals("Vence hoje", AlertProductPresentation.expiry(context, 0))
        assertEquals("Vence em 1 dia", AlertProductPresentation.expiry(context, 1))
        assertEquals("Vencido há 1 dia", AlertProductPresentation.expiry(context, -1))
        assertEquals("Vencido há 8 dias", AlertProductPresentation.expiry(context, -8))
    }

    @Test
    fun `expiry day value never renders a negative count`() {
        every { context.getString(R.string.product_control_expiration_days_today) } returns "Hoje"
        every { context.getString(R.string.product_control_expiration_days_single) } returns "1 dia"
        every { context.getString(R.string.product_control_expiration_days_past_day) } returns "1 dia atrás"
        every { context.getString(R.string.product_control_expiration_days_past, 8L) } returns "8 dias atrás"

        assertEquals("Hoje", AlertProductPresentation.expiryDays(context, 0))
        assertEquals("1 dia", AlertProductPresentation.expiryDays(context, 1))
        assertEquals("1 dia atrás", AlertProductPresentation.expiryDays(context, -1))
        assertEquals("8 dias atrás", AlertProductPresentation.expiryDays(context, -8))
    }
}
