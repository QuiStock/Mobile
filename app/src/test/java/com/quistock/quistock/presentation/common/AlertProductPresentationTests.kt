package com.quistock.quistock.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class AlertProductPresentationTests {
    @Test
    fun `quantities preserve fractions without trailing zeros or unit suffix`() {
        val locale = Locale.forLanguageTag("pt-BR")
        mapOf("5" to "5", "5.000" to "5", "2.375" to "2,375", "0.010" to "0,01").forEach { (value, expected) ->
            assertEquals(expected, AlertProductPresentation.formatStock(BigDecimal(value), locale))
        }
    }
}
