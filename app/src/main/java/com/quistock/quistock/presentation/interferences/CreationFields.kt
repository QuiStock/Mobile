package com.quistock.quistock.presentation.interferences

import android.app.DatePickerDialog
import android.widget.DatePicker
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentCreationBinding
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionField
import com.quistock.quistock.domain.model.ActionType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    private val dateInputs = mapOf(
        ActionField.ORDER_DATE to views.creationOrderDate,
        ActionField.START to views.creationStart,
        ActionField.END to views.creationEnd,
    )

    init {
        inputs.filterKeys { it !in dateInputs }.values.forEach { input ->
            input.doAfterTextChanged {
                if (!rendering) {
                    model.edit(
                        ActionDraft(
                            views.creationQuantity.text.toString(),
                            views.creationOrderDate.text.toString(),
                            views.creationDiscount.text.toString(),
                            views.creationStart.text.toString(),
                            views.creationEnd.text.toString(),
                        ),
                    )
                }
            }
        }
        dateInputs.forEach { (field, input) ->
            input.keyListener = null
            input.showSoftInputOnFocus = false
            input.isFocusable = false
            input.isFocusableInTouchMode = false
            input.isClickable = true
            input.setOnClickListener { showDatePicker(field, input) }
        }
    }

    fun render(state: CreationState) {
        rendering = true
        renderVisibility(state)
        inputs.forEach { (field, input) -> renderInput(field, input, state) }
        rendering = false
        if (state.errors != previousErrors) focusError(state)
        previousErrors = state.errors
    }

    private fun renderVisibility(state: CreationState) {
        val order = state.type == ActionType.ORDER
        val form = state.phase == CreationPhase.FORM || state.phase == CreationPhase.REVIEW
        listOf(views.creationQuantityLabel, views.creationOrderDateLabel, views.containerOrderDate).forEach {
            it.isVisible = form && order
        }
        listOf(
            views.creationDiscountLabel,
            views.creationStartLabel,
            views.containerStart,
            views.creationEndLabel,
            views.containerEnd,
        ).forEach {
            it.isVisible = form && !order
        }
        inputs.forEach { (field, input) ->
            input.isVisible = form && ((field == ActionField.QUANTITY || field == ActionField.ORDER_DATE) == order)
            input.isEnabled = state.phase == CreationPhase.FORM && !state.loading
            if (field in dateInputs) {
                input.isFocusable = false
                input.isClickable = state.phase == CreationPhase.FORM && !state.loading
            }
        }
    }

    private fun renderInput(field: ActionField, input: EditText, state: CreationState) {
        val value = when (field) {
            ActionField.QUANTITY -> state.draft.quantity
            ActionField.ORDER_DATE -> CreationDate.display(state.draft.orderDate)
            ActionField.DISCOUNT -> state.draft.discount
            ActionField.START -> CreationDate.display(state.draft.start)
            ActionField.END -> CreationDate.display(state.draft.end)
        }
        if (input.text.toString() != value) input.setText(value)
        input.error = if (field in state.errors) input.context.getString(error(field)) else null
    }

    private fun focusError(state: CreationState) {
        inputs.entries.firstOrNull { it.key in state.errors }?.value?.let { input: EditText ->
            input.requestFocus()
            input.announceForAccessibility(input.error)
        }
    }

    private fun showDatePicker(field: ActionField, input: EditText) {
        val draft = model.state.value.draft
        val current = CreationDate.date(
            when (field) {
                ActionField.ORDER_DATE -> draft.orderDate
                ActionField.START -> draft.start
                else -> draft.end
            },
        ) ?: LocalDate.now(ZoneId.of("America/Sao_Paulo"))
        DatePickerDialog(
            input.context,
            { _: DatePicker, year: Int, month: Int, day: Int ->
                val date = CreationDate.iso(year, month, day)
                model.edit(
                    when (field) {
                        ActionField.ORDER_DATE -> draft.copy(orderDate = date)
                        ActionField.START -> draft.copy(start = date)
                        else -> draft.copy(end = date)
                    },
                )
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }

    private fun error(field: ActionField): Int = when (field) {
        ActionField.QUANTITY -> R.string.creation_quantity_error
        ActionField.DISCOUNT -> R.string.creation_discount_error
        ActionField.END -> R.string.creation_end_error
        else -> R.string.creation_date_error
    }
}

internal object CreationDate {
    private val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    fun date(value: String): LocalDate? = ActionDraft.date(value)

    fun display(value: String): String = date(value)?.format(displayFormatter) ?: value

    fun iso(year: Int, month: Int, day: Int): String = LocalDate.of(year, month + 1, day).toString()
}
