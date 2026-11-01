package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionDecision
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.Justification
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.port.InterferenceRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository

/**
 * Manager review of interference requests from the manager's own store.
 * Core derives author and decision instant from the authenticated token; the client never sends them.
 */
class ManagerDecisionUseCase(
    private val repository: InterferenceRepository,
    private val products: PriorityAlertsRepository,
    private val profile: ProfileUseCase,
    private val session: SessionUseCase,
) {
    fun context(expected: SessionSnapshot): ProfileState.Ready? = (profile.state.value as? ProfileState.Ready)?.takeIf {
        it.generation == expected.generation && session.snapshot().generation == expected.generation &&
            session.state.value == SessionState.Active && it.profile.role == UserRole.MANAGER &&
            it.profile.rejection() == null
    }

    /** Requests forwarded to the manager and final decisions; employee drafts stay out of this view. */
    suspend fun list(expected: SessionSnapshot): ActionListResult {
        val ready = context(expected) ?: return ActionListResult.Failed
        return when (val result = repository.list(requireNotNull(ready.profile.storeId), null, expected)) {
            is ActionListResult.Loaded -> ActionListResult.Loaded(
                result.actions.filter { it.status.canDecide || it.status.decided },
            )

            ActionListResult.Failed -> result
        }
    }

    suspend fun catalog(expected: SessionSnapshot): AlertProductsResult {
        val ready = context(expected) ?: return AlertProductsResult.Failure
        return products.read(requireNotNull(ready.profile.storeId), ready.profile.storeCode)
    }

    suspend fun decide(
        action: InterferenceAction,
        decision: ActionDecision,
        justification: String,
        expected: SessionSnapshot,
    ): ActionResult {
        val ready = context(expected)
        val text = decision.justification(justification)
        return when {
            ready == null || ready.profile.storeId != action.storeId -> ActionResult.Failed(ActionFailure.FORBIDDEN)
            !action.status.canDecide -> ActionResult.Failed(ActionFailure.CONFLICT)
            text !is Justification.Valid -> ActionResult.Failed(ActionFailure.INVALID)
            else -> repository.decide(action, decision, text.text, expected)
        }
    }
}
