package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.CreationRisk
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
            val errors = ActionDraft(quantity = it, orderDate = today.toString()).validate(ActionType.ORDER, today)
            assertTrue(errors.isEmpty())
        }
        listOf("", "0", "-1", "1.1", "2147483648").forEach {
            val errors = ActionDraft(quantity = it, orderDate = today.toString()).validate(ActionType.ORDER, today)
            assertFalse(errors.isEmpty())
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
            val errors = draft.copy(discount = it, end = today.toString()).validate(ActionType.PROMOTION, today)
            assertTrue(errors.isEmpty())
        }
        assertFalse(draft.validate(ActionType.PROMOTION, today.plusDays(1)).isEmpty())
        assertFalse(draft.copy(end = today.minusDays(1).toString()).validate(ActionType.PROMOTION, today).isEmpty())
    }

    @Test
    fun `risk determines eligibility independently of sales speed and expired prevents promotion`() {
        StockFlow.entries.forEach { speed ->
            val product = AlertProduct(
                "product",
                "Product",
                "Category",
                BigDecimal.ONE,
                null,
                speed,
                analysisId = "flow",
                risks = setOf(CreationRisk.STOCKOUT, CreationRisk.EXPIRATION),
            )
            assertEquals(setOf(ActionType.ORDER, ActionType.PROMOTION), product.actionTypes())
            val expired = product.copy(risks = product.risks + CreationRisk.EXPIRED)
            assertEquals(setOf(ActionType.ORDER), expired.actionTypes())
            assertTrue(product.copy(risks = emptySet()).actionTypes().isEmpty())
        }
    }
}
