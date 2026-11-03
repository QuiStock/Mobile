package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.AlertMemory
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.RoleAlertType
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.port.AlertMemoryStore
import com.quistock.quistock.domain.port.AlertNotifier
import com.quistock.quistock.domain.port.AlertScheduler
import com.quistock.quistock.domain.port.InterferenceRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.port.RequestOwnership
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides which store events become device alerts for the signed-in user, honoring role,
 * per-user preferences and at-most-once delivery. Core has no push or author contract, so
 * events are polled and "my request" means a request forwarded by this user on this device.
 */
@Suppress("LongParameterList")
class RoleAlertsUseCase(
    private val products: PriorityAlertsRepository,
    private val actions: InterferenceRepository,
    private val profile: ProfileUseCase,
    private val session: SessionUseCase,
    private val memory: AlertMemoryStore,
    private val notifier: AlertNotifier,
    private val scheduler: AlertScheduler,
    private val clock: Clock = Clock.system(BUSINESS_ZONE),
) : RequestOwnership {
    private val mutex = Mutex()

    data class Settings(val role: UserRole, val enabled: Map<RoleAlertType, Boolean>)

    private class Sources(val catalog: List<AlertProduct>?, val actions: List<InterferenceAction>?)

    /** Starts periodic checks for the signed-in user and runs one immediately. */
    suspend fun start() {
        scheduler.start()
        check()
    }

    /** Session ended: no further checks and no notification left behind for the next user. */
    fun stop() {
        scheduler.stop()
        notifier.clearAll()
    }

    /** Background entry point: restores the stored session like an app start, then checks. */
    suspend fun runBackground() {
        if (!notifier.canNotify()) return
        if (session.state.value == SessionState.Restoring) session.restore()
        when (session.state.value) {
            SessionState.Active -> {
                profile.load()
                check()
            }

            SessionState.SignedOut, SessionState.Expired, SessionState.LocalFailure, is SessionState.LoggedOut -> stop()

            else -> Unit
        }
    }

    /** Returns true when Core was read for the current user. */
    suspend fun check(): Boolean = mutex.withLock {
        val expected = session.snapshot()
        val user = ready(expected)?.takeIf { notifier.canNotify() } ?: return@withLock false
        val types = RoleAlertType.forRole(user.role)
        val stored = memory.read(user.id)
        val sources = if (types.all { it in stored.disabled }) null else read(user, expected, types, stored)
        val current = sources != null && ready(expected)?.id == user.id
        if (current) deliver(user, types, stored, requireNotNull(sources))
        current
    }

    suspend fun settings(): Settings? = ready(session.snapshot())?.let { user ->
        val stored = memory.read(user.id)
        Settings(user.role, RoleAlertType.forRole(user.role).associateWith { it !in stored.disabled })
    }

    suspend fun setEnabled(type: RoleAlertType, enabled: Boolean): Boolean {
        val user = ready(session.snapshot())?.takeIf { it.role in type.roles } ?: return false
        memory.update(user.id) { it.copy(disabled = if (enabled) it.disabled - type else it.disabled + type) }
        return true
    }

    override suspend fun remember(userId: String, actionId: String) {
        memory.update(userId) { stored ->
            stored.copy(ownRequests = (stored.ownRequests + actionId).toList().takeLast(MAX_OWNED).toSet())
        }
    }

    /** A failed source is skipped for this cycle and nothing from it is marked delivered. */
    private suspend fun read(
        user: UserProfile,
        expected: SessionSnapshot,
        types: List<RoleAlertType>,
        stored: AlertMemory,
    ): Sources = try {
        val storeId = requireNotNull(user.storeId)
        val catalog = if (RoleAlertType.NEAR_EXPIRY in types) {
            (products.read(storeId, user.storeCode) as? AlertProductsResult.Success)?.products
        } else {
            null
        }
        val needsActions = RoleAlertType.PENDING_APPROVAL in types ||
            (RoleAlertType.REQUEST_APPROVED in types && stored.ownRequests.isNotEmpty())
        val list = if (needsActions) actions.list(storeId, null, expected) else null
        Sources(catalog, (list as? ActionListResult.Loaded)?.actions)
    } catch (_: SessionException) {
        Sources(null, null)
    }

    private suspend fun deliver(user: UserProfile, types: List<RoleAlertType>, stored: AlertMemory, sources: Sources) {
        val today = LocalDate.now(clock.withZone(BUSINESS_ZONE))
        val storeActions = sources.actions?.filter { it.storeId == user.storeId }
        val candidates = buildList {
            if (RoleAlertType.NEAR_EXPIRY in types) sources.catalog?.let { addAll(RoleAlertEvents.expiring(it, today)) }
            if (RoleAlertType.PENDING_APPROVAL in types) storeActions?.let { addAll(RoleAlertEvents.pending(it)) }
            if (RoleAlertType.REQUEST_APPROVED in types) {
                storeActions?.let { addAll(RoleAlertEvents.approved(it, stored.ownRequests)) }
            }
        }
        val fresh = candidates.filter { it.key !in stored.delivered }
        notifier.post(RoleAlertEvents.group(user.id, fresh.filter { it.type !in stored.disabled }))
        val settled = storeActions.orEmpty().filter { it.id in stored.ownRequests && it.status.decided }
            .map { it.id }.toSet()
        memory.update(user.id) { current ->
            current.copy(
                delivered = RoleAlertEvents.prune(current.delivered, today, storeActions) + fresh.map { it.key },
                ownRequests = current.ownRequests - settled,
            )
        }
    }

    /** Current user, only for an Active session and a Ready mobile profile of the same generation. */
    private fun ready(expected: SessionSnapshot): UserProfile? = (profile.state.value as? ProfileState.Ready)?.takeIf {
        it.generation == expected.generation && session.state.value == SessionState.Active &&
            session.snapshot().generation == expected.generation && it.profile.rejection() == null &&
            RoleAlertType.forRole(it.profile.role).isNotEmpty()
    }?.profile

    companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
        private const val MAX_OWNED = 200
    }
}
