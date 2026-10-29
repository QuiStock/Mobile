package com.quistock.quistock.presentation.interferences

import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentCreationBinding
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionField
import com.quistock.quistock.domain.model.ActionType

internal class CreationFields(
    private val views: FragmentCreationBinding,
    private val model: CreateInterferenceViewModel,
) {
    private var rendering = false
    private var previousErrors: Set<ActionField> = emptySet()
    private val inputs = linkedMapOf(
        ActionField.QUANTITY to views.creationQuantity,
        ActionField.ORDER_DATE to views.creationOrderDate,
        ActionField.DISCOUNT to views.creationDiscount,
        ActionField.START to views.creationStart,
        ActionField.END to views.creationEnd,
    )

    init {
        inputs.values.forEach { input ->
            input.doAfterTextChanged {
                if (!rendering) model.edit(
                    ActionDraft(
                        views.creationQuantity.text.toString(), views.creationOrderDate.text.toString(),
                        views.creationDiscount.text.toString(), views.creationStart.text.toString(),
                        views.creationEnd.text.toString(),
                    ),
                )
            }
        }
    }

    fun render(state: CreationState) {
        rendering = true
        val order = state.type == ActionType.ORDER
        val form = state.phase == CreationPhase.FORM || state.phase == CreationPhase.REVIEW
        listOf(views.creationQuantityLabel, views.creationOrderDateLabel).forEach { it.isVisible = form && order }
        listOf(views.creationDiscountLabel, views.creationStartLabel, views.creationEndLabel).forEach {
            it.isVisible = form && !order
        }
        inputs.forEach { (field, input) ->
            input.isVisible = form && ((field == ActionField.QUANTITY || field == ActionField.ORDER_DATE) == order)
            input.isEnabled = state.phase == CreationPhase.FORM && !state.loading
            val value = when (field) {
                ActionField.QUANTITY -> state.draft.quantity
                ActionField.ORDER_DATE -> state.draft.orderDate
                ActionField.DISCOUNT -> state.draft.discount
                ActionField.START -> state.draft.start
                ActionField.END -> state.draft.end
            }
            if (input.text.toString() != value) input.setText(value)
            input.error = if (field in state.errors) input.context.getString(error(field)) else null
        }
        rendering = false
        if (state.errors != previousErrors) focusError(state)
        previousErrors = state.errors
    }

    private fun focusError(state: CreationState) {
        inputs.entries.firstOrNull { it.key in state.errors }?.value?.let { input: EditText ->
            input.requestFocus()
            input.announceForAccessibility(input.error)
        }
    }

    private fun error(field: ActionField): Int = when (field) {
        ActionField.QUANTITY -> R.string.creation_quantity_error
        ActionField.DISCOUNT -> R.string.creation_discount_error
        ActionField.END -> R.string.creation_end_error
        else -> R.string.creation_date_error
    }
}
