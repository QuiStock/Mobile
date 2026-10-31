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
import java.time.LocalDate

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
        val action = persisted(row, flowId)
        require(action.values.validate(action.type, LocalDate.MIN).isEmpty())
        if (action.type == ActionType.PROMOTION) {
            require(action.referencePrice != null && action.promotionalPrice != null)
        }
        return action
    }

    fun persisted(row: JsonObject, flowId: String?): InterferenceAction {
        val type = ActionType.valueOf(requireNotNull(row.text("action_type")))
        val reference = row.number("reference_sale_price")
        val promotional = row.number("promotional_price")
        require(reference == null || reference.signum() >= 0)
        require(promotional == null || promotional.signum() >= 0)
        requireTypeFields(row, type)
        return InterferenceAction(
            requireNotNull(row.text("id")),
            flowId,
            requireNotNull(row.text("product_id")),
            requireNotNull(row.number("store_id")).longValueExact().also { require(it > 0) },
            type,
            ActionStatus.valueOf(requireNotNull(row.text("status"))),
            values(row),
            reference,
            promotional,
        )
    }

    private fun values(row: JsonObject): ActionDraft = ActionDraft(
        quantity = row.number("quantity")?.intValueExact()?.toString().orEmpty(),
        orderDate = row.optionalText("desired_order_date").orEmpty(),
        discount = row.number("discount_percentage")?.stripTrailingZeros()?.toPlainString().orEmpty(),
        start = row.optionalText("promotion_valid_from").orEmpty(),
        end = row.optionalText("promotion_valid_until").orEmpty(),
    )

    private fun requireTypeFields(row: JsonObject, type: ActionType) {
        val excluded = if (type == ActionType.PROMOTION) {
            listOf("quantity", "desired_order_date")
        } else {
            listOf("discount_percentage", "promotion_valid_from", "promotion_valid_until")
        }
        require(excluded.all { row[it] == null || row[it] == JsonNull })
    }

    fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }
        ?.contentOrNull?.takeIf { it.isNotBlank() }

    fun JsonObject.optionalText(key: String): String? = if (get(key) == null || get(key) == JsonNull) {
        null
    } else {
        requireNotNull(text(key))
    }

    fun JsonObject.number(key: String): BigDecimal? = if (get(key) == null || get(key) == JsonNull) {
        null
    } else {
        requireNotNull((get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toBigDecimalOrNull())
    }
}
