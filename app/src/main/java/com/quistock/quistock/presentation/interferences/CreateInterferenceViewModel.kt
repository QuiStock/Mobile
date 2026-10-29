package com.quistock.quistock.presentation.interferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionField
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.usecase.CreateInterferenceUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal enum class CreationPhase { SELECT, FORM, REVIEW, CHECKING, FORWARDING, EXISTING, SENT, EMPTY, ERROR }

internal data class CreationState(
    val phase: CreationPhase = CreationPhase.SELECT,
    val loading: Boolean = false,
    val products: List<AlertProduct> = emptyList(),
    val actions: List<InterferenceAction> = emptyList(),
    val listFailed: Boolean = false,
    val product: AlertProduct? = null,
    val type: ActionType = ActionType.ORDER,
    val draft: ActionDraft = ActionDraft(),
    val errors: Set<ActionField> = emptySet(),
    val action: InterferenceAction? = null,
    val failure: ActionFailure? = null,
) {
    val busy: Boolean get() = phase in setOf(CreationPhase.CHECKING, CreationPhase.FORWARDING)
    val mustCheckList: Boolean get() = failure == ActionFailure.UNCERTAIN
}

class CreateInterferenceViewModel(
    private val create: CreateInterferenceUseCase,
    private val session: SessionUseCase,
    profile: ProfileUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreationState())
    internal val state = mutableState.asStateFlow()
    private var bound: Pair<Long, Long?>? = null
    private var operation: Job? = null
    private var loaded = false
    private val submission = CreationSubmission(create, session, mutableState, viewModelScope) { current(it) }

    init {
        viewModelScope.launch {
            combine(session.state, profile.state) { _, current ->
                (current as? ProfileState.Ready)?.takeIf {
                    val status = session.state.value
                    it.generation == session.snapshot().generation &&
                        (status == SessionState.Active ||
                            status is SessionState.Failure) &&
                        it.profile.role == UserRole.EMPLOYEE
                }
                    ?.let { it.generation to it.profile.storeId }
            }.collect { current ->
                if (bound != current) {
                    operation?.cancel()
                    submission.cancel()
                    bound = current
                    loaded = false
                    mutableState.value = CreationState()
                }
                if (session.state.value is SessionState.Failure && state.value.busy) {
                    submission.cancel()
                    mutableState.value = state.value.copy(
                        phase = CreationPhase.ERROR, failure = ActionFailure.UNCERTAIN,
                    )
                }
            }
        }
    }

    internal fun enter(type: ActionType, productId: String? = null) {
        if (state.value.busy) return
        if (hasDraft && state.value.type != type) return
        if (state.value.type == type && (state.value.action != null ||
                (loaded && state.value.product != null))
        ) return
        load(type, productId)
    }

    internal fun load(type: ActionType = state.value.type, productId: String? = null) {
        if (state.value.busy || operation?.isActive == true) return
        val expected = session.snapshot()
        val context = create.context(expected) ?: run {
            mutableState.value = CreationState(phase = CreationPhase.ERROR, failure = ActionFailure.FORBIDDEN)
            return
        }
        bound = expected.generation to context.profile.storeId
        mutableState.value = state.value.copy(loading = true, failure = null, type = type)
        operation = viewModelScope.launch {
            val result = create.catalog(expected)
            val actions = create.list(expected)
            if (!current(expected)) return@launch
            loaded = true
            val catalog = (result as? AlertProductsResult.Success)?.products.orEmpty()
            val eligible = catalog.filter { type in it.actionTypes() }
            val product = eligible.singleOrNull { it.id == productId }
            mutableState.value = state.value.copy(
                phase = if (product == null) CreationPhase.SELECT else CreationPhase.FORM,
                loading = false,
                products = eligible,
                actions = (actions as? ActionListResult.Loaded)?.actions.orEmpty(),
                listFailed = actions !is ActionListResult.Loaded,
                product = product,
                failure = if (result == AlertProductsResult.Failure) ActionFailure.READ else null,
            )
        }
    }

    internal fun refreshList() {
        if (state.value.busy || operation?.isActive == true) return
        val expected = session.snapshot()
        val ready = create.context(expected) ?: return
        bound = expected.generation to ready.profile.storeId
        mutableState.value = state.value.copy(loading = true)
        operation = viewModelScope.launch {
            val result = create.list(expected)
            if (!current(expected)) return@launch
            mutableState.value = state.value.copy(
                loading = false,
                actions = (result as? ActionListResult.Loaded)?.actions.orEmpty(),
                listFailed = result !is ActionListResult.Loaded,
            )
        }
    }

    internal fun select(product: AlertProduct) {
        if (state.value.busy || product !in state.value.products) return
        mutableState.value = state.value.copy(
            phase = CreationPhase.FORM, product = product, action = null, failure = null,
            draft = ActionDraft(), errors = emptySet(),
        )
    }

    internal fun edit(draft: ActionDraft) {
        if (state.value.phase != CreationPhase.FORM) return
        mutableState.value = state.value.copy(draft = draft, errors = emptySet(), failure = null)
    }

    internal fun review(cancel: Boolean = false) {
        if (cancel) {
            if (state.value.phase == CreationPhase.REVIEW) {
                mutableState.value = state.value.copy(phase = CreationPhase.FORM)
            }
            return
        }
        val state = state.value
        if (state.phase != CreationPhase.FORM || state.product == null) return
        val errors = state.draft.validate(state.type, create.today())
        mutableState.value = state.copy(
            errors = errors,
            phase = if (errors.isEmpty()) CreationPhase.REVIEW else CreationPhase.FORM,
        )
    }

    internal fun confirm() = submission.confirm()

    internal fun forward() = submission.forward()

    internal fun showExisting(action: InterferenceAction) {
        if (state.value.busy || action !in state.value.actions) return
        mutableState.value = state.value.copy(
            phase = CreationPhase.EXISTING, action = action, type = action.type, failure = null,
        )
    }

    internal fun discard() {
        if (state.value.busy) return
        operation?.cancel()
        operation = null
        loaded = false
        mutableState.value = CreationState()
    }

    internal val hasDraft: Boolean get() = state.value.product != null || !state.value.draft.isEmpty()

    private fun current(expected: SessionSnapshot): Boolean =
        create.context(expected)?.let { bound == (expected.generation to it.profile.storeId) } == true
}
