package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.StockFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CreateInterferenceTests {
    private val today = LocalDate.of(2026, 10, 29)

    @Test
    fun `order accepts positive int boundary and rejects fraction zero negative overflow`() {
        listOf("1", "2147483647").forEach {
            assertTrue(ActionDraft(quantity = it, orderDate = today.toString()).validate(ActionType.ORDER, today).isEmpty())
        }
        listOf("", "0", "-1", "1.1", "2147483648").forEach {
            assertFalse(ActionDraft(quantity = it, orderDate = today.toString()).validate(ActionType.ORDER, today).isEmpty())
        }
    }

    @Test
    fun `promotion supports localized decimal and inclusive dates without expiry cap`() {
        val draft = ActionDraft(discount = "15,25", start = today.toString(), end = today.plusYears(1).toString())
        assertTrue(draft.validate(ActionType.PROMOTION, today).isEmpty())
        assertEquals(BigDecimal("15.25"), draft.discountValue())
        listOf("-1", "100.01", "1.001", "1,2.3", "").forEach {
            assertFalse(draft.copy(discount = it).validate(ActionType.PROMOTION, today).isEmpty())
        }
        listOf("0", "100", "0,00").forEach {
            assertTrue(draft.copy(discount = it, end = today.toString()).validate(ActionType.PROMOTION, today).isEmpty())
        }
        assertFalse(draft.validate(ActionType.PROMOTION, today.plusDays(1)).isEmpty())
        assertFalse(draft.copy(end = today.minusDays(1).toString()).validate(ActionType.PROMOTION, today).isEmpty())
    }

    @Test
    fun `risk determines eligibility independently of sales speed and expired prevents promotion`() {
        StockFlow.entries.forEach { speed ->
            val product = AlertProduct("product", "Product", "Category", BigDecimal.ONE, null, speed,
                analysisId = "flow", risks = setOf(CreationRisk.STOCKOUT, CreationRisk.EXPIRATION))
            assertEquals(setOf(ActionType.ORDER, ActionType.PROMOTION), product.actionTypes())
            assertEquals(setOf(ActionType.ORDER), product.copy(risks = product.risks + CreationRisk.EXPIRED).actionTypes())
            assertTrue(product.copy(risks = emptySet()).actionTypes().isEmpty())
        }
    }
}
