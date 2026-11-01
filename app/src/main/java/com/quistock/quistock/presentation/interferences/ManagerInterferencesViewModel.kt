package com.quistock.quistock.presentation.interferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.ActionDecision
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.usecase.ManagerDecisionUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal enum class DecisionPhase { IDLE, SUBMITTING, DONE, FAILED }

internal data class ManagerState(
    val loading: Boolean = false,
    val listFailed: Boolean = false,
    val actions: List<InterferenceAction> = emptyList(),
    val products: Map<String, AlertProduct> = emptyMap(),
    val filter: ActionType? = null,
    val selectedId: String? = null,
    val phase: DecisionPhase = DecisionPhase.IDLE,
    val decision: ActionDecision? = null,
    val failure: ActionFailure? = null,
) {
    val pending: List<InterferenceAction>
        get() = actions.filter { it.status.canDecide && (filter == null || it.type == filter) }
    val decided: List<InterferenceAction>
        get() = actions.filter { it.status.decided && (filter == null || it.type == filter) }
    val selected: InterferenceAction? get() = actions.firstOrNull { it.id == selectedId }
    val submitting: Boolean get() = phase == DecisionPhase.SUBMITTING

    fun product(action: InterferenceAction): AlertProduct? = products[action.productId]
}

class ManagerInterferencesViewModel(
    private val decisions: ManagerDecisionUseCase,
    private val session: SessionUseCase,
    profile: ProfileUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ManagerState())
    internal val state = mutableState.asStateFlow()
    private var bound: Pair<Long, Long?>? = null
    private var reading: Job? = null
    private var writing: Job? = null

    init {
        viewModelScope.launch {
            combine(session.state, profile.state) { _, current ->
                (current as? ProfileState.Ready)?.let { it.generation to it.profile.storeId }
            }.collect { current ->
                // A new session generation or store must never see the previous store's requests.
                if (bound != null && bound != current) reset()
            }
        }
    }

    internal fun refresh() {
        if (reading?.isActive == true || state.value.submitting) return
        val expected = session.snapshot()
        val ready = decisions.context(expected) ?: run {
            mutableState.value = ManagerState(listFailed = true, failure = ActionFailure.FORBIDDEN)
            return
        }
        bound = expected.generation to ready.profile.storeId
        mutableState.value = state.value.copy(loading = true)
        reading = viewModelScope.launch {
            val actions = guardedList(expected)
            val catalog = decisions.catalog(expected)
            if (!current(expected)) return@launch
            val products = (catalog as? AlertProductsResult.Success)?.products.orEmpty().associateBy { it.id }
            mutableState.value = state.value.copy(
                loading = false,
                listFailed = actions !is ActionListResult.Loaded,
                actions = (actions as? ActionListResult.Loaded)?.actions ?: state.value.actions,
                products = products.ifEmpty { state.value.products },
            )
        }
    }

    internal fun toggleFilter(type: ActionType) {
        mutableState.value = state.value.copy(filter = if (state.value.filter == type) null else type)
    }

    internal fun select(action: InterferenceAction): Boolean {
        if (state.value.submitting || action !in state.value.actions) return false
        mutableState.value = state.value.copy(
            selectedId = action.id,
            phase = DecisionPhase.IDLE,
            decision = null,
            failure = null,
        )
        return true
    }

    /** Sends one decision. Repeated taps, final states and concurrent writes are ignored. */
    internal fun decide(decision: ActionDecision, justification: String = "") {
        val current = state.value
        val action = current.selected ?: return
        if (current.submitting || writing?.isActive == true || !action.status.canDecide) return
        val expected = session.snapshot()
        mutableState.value = current.copy(phase = DecisionPhase.SUBMITTING, decision = decision, failure = null)
        writing = viewModelScope.launch {
            val result = try {
                decisions.decide(action, decision, justification, expected)
            } catch (_: SessionException) {
                ActionResult.Failed(ActionFailure.UNAUTHORIZED)
            }
            if (current(expected)) accept(action, result)
        }
    }

    /** Clears a shown result so a later visit does not replay feedback. */
    internal fun acknowledge() {
        if (state.value.submitting) return
        mutableState.value = state.value.copy(phase = DecisionPhase.IDLE, failure = null)
    }

    private fun accept(action: InterferenceAction, result: ActionResult) {
        val state = state.value
        when (result) {
            is ActionResult.Found -> mutableState.value = state.copy(
                actions = state.actions.map { if (it.id == action.id) result.action else it },
                phase = DecisionPhase.DONE,
                failure = null,
            )

            is ActionResult.Failed -> {
                mutableState.value = state.copy(phase = DecisionPhase.FAILED, failure = result.reason)
                // Conflict (already decided elsewhere) or an unconfirmed write: Core state is the truth.
                if (result.reason == ActionFailure.CONFLICT || result.reason == ActionFailure.UNCERTAIN) refresh()
            }

            ActionResult.Empty -> mutableState.value = state.copy(
                phase = DecisionPhase.FAILED,
                failure = ActionFailure.UNCERTAIN,
            )
        }
    }

    private suspend fun guardedList(expected: SessionSnapshot): ActionListResult = try {
        decisions.list(expected)
    } catch (_: SessionException) {
        ActionListResult.Failed
    }

    private fun reset() {
        reading?.cancel()
        writing?.cancel()
        bound = null
        mutableState.value = ManagerState()
    }

    private fun current(expected: SessionSnapshot): Boolean =
        decisions.context(expected)?.let { bound == (expected.generation to it.profile.storeId) } == true
}
