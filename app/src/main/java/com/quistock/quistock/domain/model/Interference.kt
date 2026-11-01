package com.quistock.quistock.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class ActionType { ORDER, PROMOTION }
enum class CreationRisk { STOCKOUT, NEAR_EXPIRY, EXPIRATION, EXPIRED }
enum class ActionStatus {
    GENERATED,
    IN_EMPLOYEE_TRIAGE,
    SENT_TO_MANAGER,
    APPROVED,
    REJECTED,
    ;

    val canForward: Boolean get() = this == GENERATED || this == IN_EMPLOYEE_TRIAGE
    val pending: Boolean get() = canForward || this == SENT_TO_MANAGER

    /** Core accepts a manager decision only from SENT_TO_MANAGER. */
    val canDecide: Boolean get() = this == SENT_TO_MANAGER
    val decided: Boolean get() = this == APPROVED || this == REJECTED
}

/** Manager decision; author and instant are derived by Core from the authenticated token. */
enum class ActionDecision(val status: ActionStatus) {
    APPROVE(ActionStatus.APPROVED),
    REJECT(ActionStatus.REJECTED),
    ;

    /** Trims the text; Core requires a nonblank justification only for REJECTED. */
    fun justification(text: String): Justification {
        val trimmed = text.trim()
        return when {
            trimmed.isNotEmpty() -> Justification.Valid(trimmed)
            this == REJECT -> Justification.Missing
            else -> Justification.Valid(null)
        }
    }
}

sealed interface Justification {
    data class Valid(val text: String?) : Justification
    data object Missing : Justification
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

    fun validate(type: ActionType, today: LocalDate): Set<ActionField> =
        if (type == ActionType.ORDER) orderErrors(today) else promotionErrors(today)

    private fun orderErrors(today: LocalDate): Set<ActionField> = buildSet {
        if (!INTEGER.matches(quantity.trim()) || (quantity.trim().toIntOrNull() ?: 0) <= 0) {
            add(ActionField.QUANTITY)
        }
        if (date(orderDate)?.let { !it.isBefore(today) } != true) add(ActionField.ORDER_DATE)
    }

    private fun promotionErrors(today: LocalDate): Set<ActionField> = buildSet {
        if (discountValue()?.let { it >= BigDecimal.ZERO && it <= MAX_DISCOUNT } != true) add(ActionField.DISCOUNT)
        val first = date(start)
        if (first == null || first.isBefore(today)) add(ActionField.START)
        if (date(end)?.let { first != null && !it.isBefore(first) } != true) add(ActionField.END)
    }

    fun matches(type: ActionType, stored: ActionDraft): Boolean = if (type == ActionType.ORDER) {
        quantity.trim().toIntOrNull() == stored.quantity.toIntOrNull() &&
            toIsoDate(orderDate) == toIsoDate(stored.orderDate)
    } else {
        val sameDiscount = discountValue()?.let { value ->
            stored.discountValue()?.let { value.compareTo(it) == 0 }
        } == true
        sameDiscount && toIsoDate(start) == toIsoDate(stored.start) && toIsoDate(end) == toIsoDate(stored.end)
    }

    fun isEmpty(): Boolean = listOf(quantity, orderDate, discount, start, end).all { it.isBlank() }

    companion object {
        private val INTEGER = Regex("[0-9]+")
        private val DECIMAL = Regex("[0-9]+([.,][0-9]{1,2})?")
        private val MAX_DISCOUNT = BigDecimal("100")

        private val CIVIL_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
        private val BR_DATE_DASH = Regex("[0-9]{2}-[0-9]{2}-[0-9]{4}")
        private val BR_DATE_SLASH = Regex("[0-9]{2}/[0-9]{2}/[0-9]{4}")
        private val BR_FORMATTER_DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        private val BR_FORMATTER_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun date(value: String): LocalDate? = try {
            val trimmed = value.trim()
            when {
                CIVIL_DATE.matches(trimmed) -> LocalDate.parse(trimmed)
                BR_DATE_DASH.matches(trimmed) -> LocalDate.parse(trimmed, BR_FORMATTER_DASH)
                BR_DATE_SLASH.matches(trimmed) -> LocalDate.parse(trimmed, BR_FORMATTER_SLASH)
                else -> null
            }
        } catch (_: DateTimeParseException) {
            null
        }

        fun toIsoDate(value: String): String = date(value)?.toString() ?: value.trim()

        fun formatBrDate(value: String): String = date(value)?.format(BR_FORMATTER_DASH) ?: value.trim()
    }
}

enum class ActionField { QUANTITY, ORDER_DATE, DISCOUNT, START, END }

// Memory-only, deliberately without a generated diagnostic representation.
data class InterferenceAction(
    val id: String,
    val flowId: String?,
    val productId: String,
    val storeId: Long,
    val type: ActionType,
    val status: ActionStatus,
    val values: ActionDraft,
    val referencePrice: BigDecimal? = null,
    val promotionalPrice: BigDecimal? = null,
    val productName: String? = null,
    val justification: String? = null,
    val decisionJustification: String? = null,
) {
    override fun toString(): String = "InterferenceAction"
}

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
