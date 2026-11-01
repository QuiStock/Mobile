package com.quistock.quistock.presentation.interferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class RequestFormatTests {
    @Test
    fun `promotion duration is inclusive and rejects inverted or missing periods`() {
        assertEquals(1, RequestFormat.durationDays("2026-11-05", "2026-11-05"))
        assertEquals(7, RequestFormat.durationDays("2026-11-05", "2026-11-11"))
        assertNull(RequestFormat.durationDays("2026-11-11", "2026-11-05"))
        assertNull(RequestFormat.durationDays("", "2026-11-05"))
    }

    @Test
    fun `values use Brazilian formatting without inventing missing data`() {
        assertEquals("05/11/2026", RequestFormat.date("2026-11-05"))
        assertNull(RequestFormat.date(""))
        assertEquals("12,5", RequestFormat.number(BigDecimal("12.500")))
        assertEquals("20", RequestFormat.number("20"))
        assertNull(RequestFormat.number(""))
    }
}
