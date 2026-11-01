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
    val requestedDraft: ActionDraft? = null,
    val failure: ActionFailure? = null,
) {
    val busy: Boolean get() = phase in setOf(CreationPhase.CHECKING, CreationPhase.FORWARDING)
    val mustCheckList: Boolean get() = failure == ActionFailure.UNCERTAIN
    val requestDiffersFromExisting: Boolean
        get() = requestedDraft?.let { draft -> action?.let { !draft.matches(type, it.values) } } == true
}

class CreateInterferenceViewModel(
    private val create: CreateInterferenceUseCase,
    private val session: SessionUseCase,
    private val profile: ProfileUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreationState())
    internal val state = mutableState.asStateFlow()
    private var bound: Pair<Long, Long?>? = null
    private var operation: Job? = null
    private val submission = CreationSubmission(create, session, mutableState, viewModelScope) {
        sessionCurrent(create, bound, it)
    }
    private val existingRefresh = ExistingActionRefresh(
        create,
        session,
        profile,
        mutableState,
        viewModelScope,
        { sessionCurrent(create, bound, it) },
        { bound == it },
        { bound = it },
        { operation = it },
    )

    init {
        viewModelScope.launch {
            combine(session.state, profile.state) { _, current ->
                (current as? ProfileState.Ready)?.takeIf {
                    val status = session.state.value
                    it.generation == session.snapshot().generation &&
                        (
                            status == SessionState.Active ||
                                status is SessionState.Failure
                            ) &&
                        it.profile.role == UserRole.EMPLOYEE && it.profile.rejection() == null
                }
                    ?.let { it.generation to it.profile.storeId }
            }.collect { current ->
                if (bound != current) {
                    operation?.cancel()
                    submission.cancel()
                    bound = current
                    mutableState.value = CreationState()
                }
                if (session.state.value is SessionState.Failure && state.value.busy) {
                    submission.cancel()
                    mutableState.value = state.value.copy(
                        phase = CreationPhase.ERROR,
                        failure = ActionFailure.UNCERTAIN,
                    )
                }
            }
        }
    }

    internal fun enter(type: ActionType, productId: String? = null) {
        val state = state.value
        val retained = hasDraft || state.action != null
        if (!state.busy && !retained) load(type, productId)
    }

    internal fun load(type: ActionType = state.value.type, productId: String? = null) {
        if (state.value.busy || operation?.isActive == true) return
        val expected = session.snapshot()
        val context = create.context(expected) ?: run {
            mutableState.value = CreationState(phase = CreationPhase.ERROR, failure = ActionFailure.FORBIDDEN)
            return
        }
        val expectedBinding = expected.generation to context.profile.storeId
        bound = expectedBinding
        val previous = state.value.product
        mutableState.value = state.value.copy(loading = true, failure = null, type = type)
        operation = viewModelScope.launch {
            loadCatalog(create, mutableState, expected, expectedBinding, type, previous, productId, {
                sessionCurrent(create, bound, it)
            }) { expectedSnapshot, expectedStore ->
                finishFailedRead(session, profile, mutableState, bound, expectedSnapshot, expectedStore)
            }
        }
    }

    internal fun refreshList() {
        if (state.value.busy || operation?.isActive == true) return
        val expected = session.snapshot()
        val ready = create.context(expected) ?: return
        val expectedBinding = expected.generation to ready.profile.storeId
        bound = expectedBinding
        mutableState.value = state.value.copy(loading = true)
        operation = viewModelScope.launch {
            val result = create.list(expected)
            if (!sessionCurrent(create, bound, expected)) {
                finishFailedRead(session, profile, mutableState, bound, expected, expectedBinding)
                return@launch
            }
            mutableState.value = state.value.copy(
                loading = false,
                actions = (result as? ActionListResult.Loaded)?.actions.orEmpty(),
                listFailed = result !is ActionListResult.Loaded,
                failure = if (result is ActionListResult.Loaded) null else ActionFailure.READ,
            )
        }
    }

    internal fun refreshExisting() {
        existingRefresh.refresh(state.value, operation?.isActive == true)
    }

    internal fun select(product: AlertProduct) {
        if (state.value.busy || state.value.loading || product !in state.value.products) return
        val current = state.value
        if (current.listFailed) {
            mutableState.value = current.copy(failure = ActionFailure.READ)
            return
        }
        val existing = findExistingAction(product, current.type, current.actions, bound?.second)
        mutableState.value = state.value.copy(
            phase = if (existing == null) CreationPhase.FORM else CreationPhase.EXISTING,
            product = product,
            action = existing,
            failure = null,
            draft = ActionDraft(),
            errors = emptySet(),
        )
    }

    internal fun edit(draft: ActionDraft) {
        if (state.value.phase != CreationPhase.FORM || state.value.loading) return
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
        if (!canReview(state)) return
        val errors = state.draft.validate(state.type, create.today())
        mutableState.value = state.copy(
            errors = errors,
            phase = if (errors.isEmpty()) CreationPhase.REVIEW else CreationPhase.FORM,
        )
    }

    internal fun confirm() = submission.confirm()

    internal fun forward() = submission.forward()

    internal fun showExisting(action: InterferenceAction) {
        val current = state.value
        val viewAvailable = !current.busy && !current.loading
        val selectionAvailable = operation?.isActive != true && action in current.actions
        if (!viewAvailable || !selectionAvailable) return
        mutableState.value = current.copy(
            phase = CreationPhase.EXISTING,
            action = action,
            type = action.type,
            failure = null,
            product = null,
            draft = ActionDraft(),
        )
    }

    internal fun discard() {
        if (state.value.busy) return
        operation?.cancel()
        operation = null
        mutableState.value = CreationState()
    }

    internal val hasDraft: Boolean get() = state.value.product != null || !state.value.draft.isEmpty()
}

private suspend fun loadCatalog(
    create: CreateInterferenceUseCase,
    state: MutableStateFlow<CreationState>,
    expected: SessionSnapshot,
    expectedBinding: Pair<Long, Long?>,
    type: ActionType,
    previous: AlertProduct?,
    productId: String?,
    isCurrent: (SessionSnapshot) -> Boolean,
    finishFailedRead: (SessionSnapshot, Pair<Long, Long?>) -> Unit,
) {
    val result = create.catalog(expected)
    val actions = create.list(expected)
    if (!isCurrent(expected)) return finishFailedRead(expected, expectedBinding)
    val eligible = (result as? AlertProductsResult.Success)?.products.orEmpty().filter { type in it.actionTypes() }
    val candidate = eligible.singleOrNull { it.id == (productId ?: previous?.id) }
    val changed = previous != null && candidate?.analysisId != previous.analysisId
    val listFailed = actions !is ActionListResult.Loaded
    val product = candidate.takeUnless { changed || listFailed }
    val loadedActions = (actions as? ActionListResult.Loaded)?.actions.orEmpty()
    val existing = product?.let { findExistingAction(it, type, loadedActions, expectedBinding.second) }
    state.value = state.value.copy(
        phase = productPhase(product, existing),
        loading = false,
        products = eligible,
        actions = loadedActions,
        listFailed = listFailed,
        product = product,
        action = existing,
        failure = loadFailure(listFailed, result, changed),
    )
}

private fun sessionCurrent(
    create: CreateInterferenceUseCase,
    bound: Pair<Long, Long?>?,
    expected: SessionSnapshot,
): Boolean = create.context(expected)?.let { bound == (expected.generation to it.profile.storeId) } == true

private fun finishFailedRead(
    session: SessionUseCase,
    profile: ProfileUseCase,
    state: MutableStateFlow<CreationState>,
    bound: Pair<Long, Long?>?,
    expected: SessionSnapshot,
    expectedBinding: Pair<Long, Long?>,
) {
    val profileBinding = (profile.state.value as? ProfileState.Ready)?.let { it.generation to it.profile.storeId }
    val sessionUnchanged = session.snapshot().generation == expected.generation
    val bindingUnchanged = bound == expectedBinding && profileBinding == expectedBinding
    if (session.state.value is SessionState.Failure && sessionUnchanged && bindingUnchanged) {
        state.value = state.value.copy(loading = false, listFailed = true, failure = ActionFailure.UNAUTHORIZED)
    }
}

private fun findExistingAction(
    product: AlertProduct,
    type: ActionType,
    actions: List<InterferenceAction>,
    storeId: Long?,
): InterferenceAction? = actions.filter {
    it.storeId == storeId && it.productId == product.id && it.type == type
}.let { matching ->
    matching.firstOrNull { it.status.pending } ?: matching.firstOrNull { it.flowId == product.analysisId }
}

private fun productPhase(product: AlertProduct?, existing: InterferenceAction?): CreationPhase = when {
    product == null -> CreationPhase.SELECT
    existing != null -> CreationPhase.EXISTING
    else -> CreationPhase.FORM
}

private fun loadFailure(listFailed: Boolean, result: AlertProductsResult, changed: Boolean): ActionFailure? = when {
    listFailed || result == AlertProductsResult.Failure -> ActionFailure.READ
    changed -> ActionFailure.CONFLICT
    else -> null
}

private fun canReview(state: CreationState): Boolean {
    val formAvailable = state.phase == CreationPhase.FORM && state.product != null
    val blocked = state.listFailed || state.failure == ActionFailure.UNAUTHORIZED
    return formAvailable && !state.loading && !blocked
}

private class ExistingActionRefresh(
    private val create: CreateInterferenceUseCase,
    private val session: SessionUseCase,
    private val profile: ProfileUseCase,
    private val state: MutableStateFlow<CreationState>,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val isCurrent: (SessionSnapshot) -> Boolean,
    private val isBound: (Pair<Long, Long?>) -> Boolean,
    private val bind: (Pair<Long, Long?>) -> Unit,
    private val onOperation: (Job) -> Unit,
) {
    fun refresh(previous: CreationState, operationActive: Boolean) {
        val action = previous.action
        val existingAvailable = previous.phase == CreationPhase.EXISTING && !previous.busy
        if (!existingAvailable || operationActive || action == null) return
        val expected = session.snapshot()
        val ready = create.context(expected)
        if (ready == null) {
            state.value = previous.copy(failure = ActionFailure.FORBIDDEN)
        } else {
            start(previous, action, expected, expected.generation to ready.profile.storeId)
        }
    }

    private fun start(
        previous: CreationState,
        action: InterferenceAction,
        expected: SessionSnapshot,
        expectedBinding: Pair<Long, Long?>,
    ) {
        bind(expectedBinding)
        state.value = previous.copy(loading = true, failure = null)
        onOperation(
            scope.launch {
                val result = create.list(expected)
                if (!isCurrent(expected)) {
                    finishFailed(expected, expectedBinding, action)
                } else if (state.value.action?.id == action.id) {
                    apply(result, action)
                }
            },
        )
    }

    private fun apply(result: ActionListResult, action: InterferenceAction) {
        val actions = (result as? ActionListResult.Loaded)?.actions
        val refreshed = actions?.firstOrNull { it.id == action.id }
        state.value = state.value.copy(
            loading = false,
            actions = actions ?: state.value.actions,
            action = refreshed ?: action,
            failure = when {
                actions == null -> ActionFailure.READ
                refreshed == null -> ActionFailure.NOT_FOUND
                else -> null
            },
        )
    }

    private fun finishFailed(
        expected: SessionSnapshot,
        expectedBinding: Pair<Long, Long?>,
        action: InterferenceAction,
    ) {
        val currentState = state.value
        val profileBinding = (profile.state.value as? ProfileState.Ready)?.let { it.generation to it.profile.storeId }
        val sameSession = session.snapshot().generation == expected.generation && isBound(expectedBinding)
        val sameAction = currentState.action?.id == action.id
        if (sameSession && sameAction && profileBinding == expectedBinding) {
            state.value = currentState.copy(loading = false, failure = ActionFailure.UNAUTHORIZED)
        }
    }
}
