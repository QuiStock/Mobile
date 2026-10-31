package com.quistock.quistock.data.remote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSummaryMapperTests {
    private fun json(near: String = "4", stockout: String = "2", overstock: String = "3", actions: String = "5") =
        """{"near_expiry_products":$near,"stockout_products":$stockout,"overstock_products":$overstock,"active_actions":$actions,"extra":true}"""

    @Test fun validCountsMapToCardsAndZeroIsValid() {
        val summary = DashboardSummaryMapper.parse(json())
        assertEquals(4L, summary.nearExpiry)
        assertEquals(5L, summary.stockoutAndOverstock)
        assertEquals(5L, summary.activeActions)
        assertEquals(0L, DashboardSummaryMapper.parse(json("0", "0", "0", "0")).nearExpiry)
    }

    @Test fun invalidOrMissingCountsAndOverflowNeverBecomeZero() {
        for (value in listOf("null", "-1", "1.5", "1e0", "\"2\"", "9223372036854775808")) {
            assertTrue(value, runCatching { DashboardSummaryMapper.parse(json(near = value)) }.isFailure)
        }
        for (value in listOf("{}", "[]", "", "not-json")) {
            assertTrue(runCatching { DashboardSummaryMapper.parse(value) }.isFailure)
        }
        assertTrue(runCatching { DashboardSummaryMapper.parse(json(stockout = Long.MAX_VALUE.toString())) }.isFailure)
    }
}
