package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.math.BigDecimal

internal object InterferenceMapper {
    fun request(flowId: String, type: ActionType, draft: ActionDraft): JsonObject = buildJsonObject {
        put("flow_id", flowId)
        put("action_type", type.name)
        if (type == ActionType.ORDER) {
            put("quantity", draft.quantity.trim().toInt())
            put("desired_order_date", draft.orderDate)
        } else {
            put("discount_percentage", JsonPrimitive(requireNotNull(draft.discountValue())))
            put("promotion_valid_from", draft.start)
            put("promotion_valid_until", draft.end)
        }
    }

    fun action(row: JsonObject, flowId: String = requireNotNull(row.text("flow_id"))): InterferenceAction {
        val type = ActionType.valueOf(requireNotNull(row.text("action_type")))
        val values = ActionDraft(
            quantity = row.number("quantity")?.intValueExact()?.toString().orEmpty(),
            orderDate = row.text("desired_order_date").orEmpty(),
            discount = row.number("discount_percentage")?.stripTrailingZeros()?.toPlainString().orEmpty(),
            start = row.text("promotion_valid_from").orEmpty(),
            end = row.text("promotion_valid_until").orEmpty(),
        )
        // Stored dates can be in the past. Validate shape/ranges without rejecting historical actions.
        require(values.validate(type, java.time.LocalDate.MIN).isEmpty())
        val reference = row.number("reference_sale_price")
        val promotional = row.number("promotional_price")
        if (type == ActionType.PROMOTION) {
            require(reference != null && reference.signum() >= 0 && promotional != null && promotional.signum() >= 0)
            require(row["quantity"] == null || row["quantity"] == JsonNull)
            require(row["desired_order_date"] == null || row["desired_order_date"] == JsonNull)
        } else {
            require(
                listOf("discount_percentage", "promotion_valid_from", "promotion_valid_until").all {
                    row[it] == null || row[it] == JsonNull
                },
            )
        }
        return InterferenceAction(
            requireNotNull(row.text("id")), flowId, requireNotNull(row.text("product_id")),
            requireNotNull(row.number("store_id")).longValueExact().also { require(it > 0) }, type,
            ActionStatus.valueOf(requireNotNull(row.text("status"))), values, reference, promotional,
        )
    }

    fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }
        ?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): BigDecimal? = (get(key) as? JsonPrimitive)
        ?.takeIf { !it.isString }?.contentOrNull?.toBigDecimalOrNull()
}
