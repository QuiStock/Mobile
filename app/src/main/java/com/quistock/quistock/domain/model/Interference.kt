package com.quistock.quistock.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class ActionType { ORDER, PROMOTION }
enum class CreationRisk { STOCKOUT, NEAR_EXPIRY, EXPIRATION, EXPIRED }
enum class ActionStatus {
    GENERATED, IN_EMPLOYEE_TRIAGE, SENT_TO_MANAGER, APPROVED, REJECTED;

    val canForward: Boolean get() = this == GENERATED || this == IN_EMPLOYEE_TRIAGE
    val pending: Boolean get() = canForward || this == SENT_TO_MANAGER
}

data class ActionDraft(
    val quantity: String = "",
    val orderDate: String = "",
    val discount: String = "",
    val start: String = "",
    val end: String = "",
) {
    fun discountValue(): BigDecimal? = discount.trim().takeIf { DECIMAL.matches(it) }
        ?.replace(',', '.')?.toBigDecimalOrNull()

    fun validate(type: ActionType, today: LocalDate): Set<ActionField> = buildSet {
        if (type == ActionType.ORDER) {
            if (!INTEGER.matches(quantity.trim()) || (quantity.trim().toIntOrNull() ?: 0) <= 0) add(ActionField.QUANTITY)
            if (date(orderDate)?.let { !it.isBefore(today) } != true) add(ActionField.ORDER_DATE)
        } else {
            if (discountValue()?.let { it >= BigDecimal.ZERO && it <= MAX_DISCOUNT } != true) add(ActionField.DISCOUNT)
            val first = date(start)
            if (first == null || first.isBefore(today)) add(ActionField.START)
            if (date(end)?.let { first != null && !it.isBefore(first) } != true) add(ActionField.END)
        }
    }

    fun isEmpty(): Boolean = listOf(quantity, orderDate, discount, start, end).all { it.isBlank() }

    companion object {
        private val INTEGER = Regex("[0-9]+")
        private val DECIMAL = Regex("[0-9]+([.,][0-9]{1,2})?")
        private val MAX_DISCOUNT = BigDecimal("100")

        fun date(value: String): LocalDate? = try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

enum class ActionField { QUANTITY, ORDER_DATE, DISCOUNT, START, END }

// Memory-only, deliberately without a generated diagnostic representation.
class InterferenceAction(
    val id: String,
    val flowId: String,
    val productId: String,
    val storeId: Long,
    val type: ActionType,
    val status: ActionStatus,
    val values: ActionDraft,
    val referencePrice: BigDecimal? = null,
    val promotionalPrice: BigDecimal? = null,
)

enum class ActionFailure { INVALID, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, READ, REJECTED, UNCERTAIN }

sealed interface ActionResult {
    class Found(val action: InterferenceAction, val existing: Boolean = false) : ActionResult
    class Failed(val reason: ActionFailure) : ActionResult
    data object Empty : ActionResult
}

sealed interface ActionListResult {
    class Loaded(val actions: List<InterferenceAction>) : ActionListResult
    data object Failed : ActionListResult
}
