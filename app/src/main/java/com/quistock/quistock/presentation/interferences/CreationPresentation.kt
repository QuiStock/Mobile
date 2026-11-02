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

    fun existingNotice(state: CreationState): Int? = when {
        state.phase != CreationPhase.EXISTING -> null
        state.requestedDraft == null -> existingActionNotice(state)
        else -> reusedActionNotice(state)
    }

    private fun existingActionNotice(state: CreationState): Int? {
        val action = state.action
        if (state.product == null || action == null) return null
        return refreshNotice(state) ?: if (action.status.pending) {
            R.string.creation_pending_exists
        } else {
            R.string.creation_final_exists
        }
    }

    private fun reusedActionNotice(state: CreationState): Int = refreshNotice(state) ?: if (
        state.requestDiffersFromExisting
    ) {
        R.string.creation_existing_reused_different
    } else {
        R.string.creation_existing_reused_same
    }

    private fun refreshNotice(state: CreationState): Int? = when {
        state.loading -> R.string.creation_action_refreshing
        state.failure == ActionFailure.READ -> R.string.creation_action_refresh_error
        state.failure == ActionFailure.NOT_FOUND -> R.string.creation_action_missing
        state.failure != null -> failure(state.failure)
        else -> null
    }

    fun values(context: Context, type: ActionType, draft: ActionDraft): String = if (type == ActionType.ORDER) {
        context.getString(
            R.string.creation_order_summary,
            display(context, draft.quantity),
            display(context, CreationDate.display(draft.orderDate)),
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
            display(context, CreationDate.display(draft.start)),
            display(context, CreationDate.display(draft.end)),
        )
    }

    fun product(context: Context, product: AlertProduct): String = context.getString(
        R.string.creation_product_summary,
        product.name,
        product.category,
        product.stock.stripTrailingZeros().toPlainString(),
        CreationCardFormatter.formatAnalysisDate(product.analysisDate?.toString()),
    )

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
}

internal object CreationActionPresentation {
    fun action(context: Context, action: InterferenceAction, productName: String? = null): String {
        val summary = context.getString(
            R.string.creation_action_summary,
            context.getString(CreationPresentation.type(action.type)),
            displayProductName(action, productName, context),
            context.getString(status(action.status)),
            CreationPresentation.values(context, action.type, action.values),
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

    fun actionTitle(context: Context, action: InterferenceAction, productName: String? = null): String =
        context.getString(
            R.string.creation_action_title,
            context.getString(CreationPresentation.type(action.type)),
            displayProductName(action, productName, context),
        )

    fun actionSubtitle(context: Context, action: InterferenceAction): String {
        val value = if (action.type == ActionType.ORDER) {
            context.getString(R.string.creation_action_quantity, display(context, action.values.quantity))
        } else {
            context.getString(
                R.string.creation_action_discount,
                if (action.values.discount.isBlank()) {
                    context.getString(R.string.creation_not_informed)
                } else {
                    context.getString(R.string.creation_percentage_value, action.values.discount)
                },
            )
        }
        return context.getString(R.string.creation_action_subtitle, context.getString(status(action.status)), value)
    }

    private fun displayProductName(action: InterferenceAction, productName: String?, context: Context): String =
        (productName ?: action.productName)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.creation_product_unidentified)

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
