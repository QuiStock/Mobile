package com.quistock.quistock.data.remote.core

import com.quistock.quistock.data.remote.core.InterferenceMapper.optionalText
import com.quistock.quistock.data.remote.core.InterferenceMapper.text
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.LocalDate

/** Existing records may predate the mandatory human fields used by new generations. */
internal object InterferenceListMapper {
    fun action(row: JsonObject): InterferenceAction? {
        if (row.text("action_type") == "MONITOR") return null
        val action = InterferenceMapper.persisted(row, row.optionalText("flow_id"))
        when (action.type) {
            ActionType.ORDER -> validateOrder(action.values)
            ActionType.PROMOTION -> validatePromotion(action.values)
        }
        return action
    }

    private fun validateOrder(values: ActionDraft) {
        require(values.quantity.isEmpty() || values.quantity.toIntOrNull()?.let { it > 0 } == true)
        date(values.orderDate)
    }

    private fun validatePromotion(values: ActionDraft) {
        validateDiscount(values.discount)
        val start = date(values.start)
        val end = date(values.end)
        require(start == null || end == null || !end.isBefore(start))
    }

    private fun validateDiscount(value: String) {
        val discount = value.toBigDecimalOrNull()
        require(value.isEmpty() || (discount != null && discount >= BigDecimal.ZERO && discount <= MAX_DISCOUNT))
        require(discount == null || discount.stripTrailingZeros().scale() <= DISCOUNT_DECIMALS)
    }

    private fun date(value: String): LocalDate? = if (value.isEmpty()) null else requireNotNull(ActionDraft.date(value))

    private val MAX_DISCOUNT = BigDecimal("100")
    private const val DISCOUNT_DECIMALS = 2
}
