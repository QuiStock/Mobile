package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.port.InterferenceRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.model.AlertProductsResult
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class CreateInterferenceUseCase(
    private val repository: InterferenceRepository,
    private val products: PriorityAlertsRepository,
    private val profile: ProfileUseCase,
    private val session: SessionUseCase,
    private val clock: Clock = Clock.system(ZoneId.of("America/Sao_Paulo")),
) {
    fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")))

    fun context(expected: SessionSnapshot): ProfileState.Ready? = (profile.state.value as? ProfileState.Ready)?.takeIf {
        it.generation == expected.generation && session.snapshot().generation == expected.generation &&
            session.state.value == SessionState.Active && it.profile.role == UserRole.EMPLOYEE &&
            it.profile.rejection() == null
    }

    suspend fun catalog(expected: SessionSnapshot): AlertProductsResult {
        val ready = context(expected) ?: return AlertProductsResult.Failure
        return products.read(requireNotNull(ready.profile.storeId), ready.profile.storeCode)
    }

    suspend fun list(expected: SessionSnapshot, type: ActionType? = null): ActionListResult {
        val ready = context(expected) ?: return ActionListResult.Failed
        return repository.list(requireNotNull(ready.profile.storeId), type, expected)
    }

    suspend fun generate(
        product: AlertProduct,
        type: ActionType,
        draft: ActionDraft,
        expected: SessionSnapshot,
    ): ActionResult {
        val ready = context(expected) ?: return ActionResult.Failed(ActionFailure.FORBIDDEN)
        if (type !in product.actionTypes() || draft.validate(type, today()).isNotEmpty()) {
            return ActionResult.Failed(ActionFailure.INVALID)
        }
        val storeId = requireNotNull(ready.profile.storeId)
        val actions = repository.list(storeId, type, expected)
        if (actions !is ActionListResult.Loaded) return ActionResult.Failed(ActionFailure.READ)
        val matching = actions.actions.filter { it.productId == product.id && it.type == type && it.storeId == storeId }
        val existing = matching.firstOrNull { it.flowId == product.analysisId }
            ?: matching.firstOrNull { it.status.pending }
        if (existing != null) return ActionResult.Found(existing, existing = true)
        // Read current analysis again. Never silently substitute a new flow for the selected one.
        val current = catalog(expected) as? AlertProductsResult.Success
            ?: return ActionResult.Failed(ActionFailure.READ)
        val selected = current.products.singleOrNull { it.id == product.id }
        if (selected == null || selected.analysisId != product.analysisId || type !in selected.actionTypes()) {
            return ActionResult.Failed(ActionFailure.CONFLICT)
        }
        if (context(expected)?.profile?.storeId != storeId) return ActionResult.Failed(ActionFailure.FORBIDDEN)
        return repository.generate(selected, storeId, type, draft, expected)
    }

    suspend fun forward(action: InterferenceAction, expected: SessionSnapshot): ActionResult {
        val ready = context(expected) ?: return ActionResult.Failed(ActionFailure.FORBIDDEN)
        if (ready.profile.storeId != action.storeId || !action.status.canForward) {
            return ActionResult.Failed(ActionFailure.FORBIDDEN)
        }
        return repository.forward(action, expected)
    }
}
