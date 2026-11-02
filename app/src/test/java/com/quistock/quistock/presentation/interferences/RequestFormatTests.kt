package com.quistock.quistock.presentation.interferences

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

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

    @Test
    fun `action draft dates parse civil and brazilian formats with dashes and slashes`() {
        assertEquals(LocalDate.of(2026, 11, 1), ActionDraft.date("2026-11-01"))
        assertEquals(LocalDate.of(2026, 11, 1), ActionDraft.date("01-11-2026"))
        assertEquals(LocalDate.of(2026, 11, 1), ActionDraft.date("01/11/2026"))
        assertNull(ActionDraft.date("invalid-date"))
        assertNull(ActionDraft.date(""))

        assertEquals("2026-11-01", ActionDraft.toIsoDate("01-11-2026"))
        assertEquals("2026-11-01", ActionDraft.toIsoDate("01/11/2026"))
        assertEquals("2026-11-01", ActionDraft.toIsoDate("2026-11-01"))
        assertEquals("custom", ActionDraft.toIsoDate("custom"))

        assertEquals("01-11-2026", ActionDraft.formatBrDate("2026-11-01"))
        assertEquals("01-11-2026", ActionDraft.formatBrDate("01/11/2026"))
        assertEquals("custom", ActionDraft.formatBrDate("custom"))
    }

    @Test
    fun `action draft matches ignores date representation differences`() {
        val order1 = ActionDraft(quantity = "10", orderDate = "2026-11-01")
        val order2 = ActionDraft(quantity = "10", orderDate = "01-11-2026")
        val order3 = ActionDraft(quantity = "15", orderDate = "01-11-2026")
        assertTrue(order1.matches(ActionType.ORDER, order2))
        assertFalse(order1.matches(ActionType.ORDER, order3))

        val promo1 = ActionDraft(discount = "10", start = "2026-11-01", end = "2026-11-10")
        val promo2 = ActionDraft(discount = "10,00", start = "01/11/2026", end = "10-11-2026")
        val promo3 = ActionDraft(discount = "20", start = "01/11/2026", end = "10-11-2026")
        assertTrue(promo1.matches(ActionType.PROMOTION, promo2))
        assertFalse(promo1.matches(ActionType.PROMOTION, promo3))
    }

    @Test
    fun `creation card formatter parses analysis dates correctly`() {
        val formatted = CreationCardFormatter.formatAnalysisDate("2026-10-20T10:00:00Z")
        assertTrue(formatted.contains("2026"))
        assertEquals("20-10-2026", CreationCardFormatter.formatAnalysisDate("2026-10-20"))
        assertEquals("", CreationCardFormatter.formatAnalysisDate(null))
        assertEquals("", CreationCardFormatter.formatAnalysisDate("   "))
        assertEquals("raw-val", CreationCardFormatter.formatAnalysisDate("raw-val"))
    }
}
