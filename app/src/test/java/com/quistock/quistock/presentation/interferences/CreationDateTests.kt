package com.quistock.quistock.presentation.interferences

import org.junit.Assert.assertEquals
import org.junit.Test

class CreationDateTests {
    @Test
    fun `displays ISO dates as day month year and keeps invalid input visible`() {
        assertEquals("05/11/2026", CreationDate.display("2026-11-05"))
        assertEquals("invalid", CreationDate.display("invalid"))
    }

    @Test
    fun `converts picker month and date to ISO`() {
        assertEquals("2026-11-05", CreationDate.iso(2026, 10, 5))
    }
}
