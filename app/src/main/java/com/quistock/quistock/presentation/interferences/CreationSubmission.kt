package com.quistock.quistock.presentation.interferences

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.usecase.CreateInterferenceUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class CreationSubmission(
    private val create: CreateInterferenceUseCase,
    private val session: SessionUseCase,
    private val mutableState: MutableStateFlow<CreationState>,
    private val scope: CoroutineScope,
    private val current: (SessionSnapshot) -> Boolean,
) {
    private val state get() = mutableState
    private var operation: Job? = null

    fun cancel() {
        operation?.cancel()
        operation = null
    }

    internal fun confirm() {
        val state = state.value
        val product = state.product ?: return
        if (state.phase != CreationPhase.REVIEW || operation?.isActive == true) return
        val errors = state.draft.validate(state.type, create.today())
        if (errors.isNotEmpty()) {
            mutableState.value = state.copy(phase = CreationPhase.FORM, errors = errors)
            return
        }
        val expected = session.snapshot()
        mutableState.value = state.copy(phase = CreationPhase.CHECKING, failure = null)
        operation = scope.launch {
            guarded(expected) {
                val result = create.generate(product, state.type, state.draft, expected)
                if (!current(expected)) return@guarded
                if (result is ActionResult.Found && !result.existing && result.action.status.canForward) {
                    mutableState.value = mutableState.value.copy(
                        phase = CreationPhase.FORWARDING, action = result.action,
                    )
                    accept(create.forward(result.action, expected), expected)
                } else {
                    accept(result, expected)
                }
            }
        }
    }

    internal fun forward() {
        val state = state.value
        val action = state.action ?: return
        if (state.phase != CreationPhase.EXISTING || !action.status.canForward || operation?.isActive == true) return
        val expected = session.snapshot()
        mutableState.value = state.copy(phase = CreationPhase.FORWARDING, failure = null)
        operation = scope.launch { guarded(expected) { accept(create.forward(action, expected), expected) } }
    }

    private suspend fun guarded(expected: SessionSnapshot, block: suspend () -> Unit) {
        try {
            block()
        } catch (_: SessionException) {
            if (current(expected)) accept(ActionResult.Failed(ActionFailure.UNAUTHORIZED), expected)
        }
    }

    private fun accept(result: ActionResult, expected: SessionSnapshot) {
        if (!current(expected)) return
        val state = state.value
        mutableState.value = when (result) {
            is ActionResult.Found -> state.copy(
                phase = if (result.action.status == ActionStatus.SENT_TO_MANAGER) {
                    CreationPhase.SENT
                } else {
                    CreationPhase.EXISTING
                },
                action = result.action, draft = ActionDraft(), failure = null,
            )
            is ActionResult.Failed -> state.copy(
                phase = if (state.action != null && result.reason != ActionFailure.UNCERTAIN) {
                    CreationPhase.EXISTING
                } else if (result.reason in setOf(ActionFailure.INVALID, ActionFailure.UNAUTHORIZED,
                        ActionFailure.READ, ActionFailure.REJECTED)
                ) {
                    CreationPhase.FORM
                } else {
                    CreationPhase.ERROR
                },
                failure = result.reason,
            )
            ActionResult.Empty -> state.copy(phase = CreationPhase.EMPTY, action = null)
        }
    }
}
