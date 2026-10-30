package com.quistock.quistock.presentation.interferences

import android.content.Context
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.InterferenceAction

internal object CreationPresentation {
    fun type(type: ActionType): Int =
        if (type == ActionType.ORDER) R.string.creation_order else R.string.creation_promotion

    fun values(context: Context, type: ActionType, draft: ActionDraft): String = if (type == ActionType.ORDER) {
        context.getString(
            R.string.creation_order_summary,
            display(context, draft.quantity),
            display(context, draft.orderDate),
        )
    } else {
        val discount = if (draft.discount.isBlank()) {
            context.getString(R.string.creation_not_informed)
        } else {
            context.getString(R.string.creation_percentage_value, draft.discount)
        }
        context.getString(
            R.string.creation_promotion_summary,
            discount,
            display(context, draft.start),
            display(context, draft.end),
        )
    }

    fun product(context: Context, product: AlertProduct): String = context.getString(
        R.string.creation_product_summary,
        product.name,
        product.category,
        product.stock.stripTrailingZeros().toPlainString(),
        product.analysisDate?.toString().orEmpty(),
    )

    fun action(context: Context, action: InterferenceAction): String {
        val summary = context.getString(
            R.string.creation_action_summary,
            context.getString(type(action.type)),
            action.productId,
            context.getString(status(action.status)),
            values(context, action.type, action.values),
        )
        return if (action.type == ActionType.PROMOTION) {
            summary + "\n" + context.getString(
                R.string.creation_prices,
                display(context, action.referencePrice?.toPlainString()),
                display(context, action.promotionalPrice?.toPlainString()),
            )
        } else {
            summary
        }
    }

    fun failure(reason: ActionFailure): Int = when (reason) {
        ActionFailure.INVALID -> R.string.creation_invalid
        ActionFailure.UNAUTHORIZED -> R.string.creation_unauthorized
        ActionFailure.FORBIDDEN -> R.string.creation_forbidden
        ActionFailure.NOT_FOUND -> R.string.creation_not_found
        ActionFailure.CONFLICT -> R.string.creation_conflict
        ActionFailure.READ -> R.string.creation_read_error
        ActionFailure.REJECTED -> R.string.creation_rejected
        ActionFailure.UNCERTAIN -> R.string.creation_uncertain
    }

    private fun display(context: Context, value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: context.getString(R.string.creation_not_informed)

    private fun status(status: ActionStatus): Int = when (status) {
        ActionStatus.GENERATED -> R.string.creation_status_generated
        ActionStatus.IN_EMPLOYEE_TRIAGE -> R.string.creation_status_triage
        ActionStatus.SENT_TO_MANAGER -> R.string.creation_status_sent
        ActionStatus.APPROVED -> R.string.creation_status_approved
        ActionStatus.REJECTED -> R.string.creation_status_rejected
    }
}
