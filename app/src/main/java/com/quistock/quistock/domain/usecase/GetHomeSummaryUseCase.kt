package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummaryResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.SummaryFailure
import com.quistock.quistock.domain.port.HomeSummaryCache
import com.quistock.quistock.domain.port.HomeSummaryRepository
import kotlinx.coroutines.CancellationException

class GetHomeSummaryUseCase(
    private val repository: HomeSummaryRepository,
    private val cache: HomeSummaryCache,
    private val session: SessionUseCase,
    private val profile: ProfileUseCase,
) {
    @Volatile var retryAt: Long = 0
        private set

    @Suppress("TooGenericExceptionCaught")
    suspend fun cached(): CachedHomeSummary? {
        val scope = session.local.binding()?.takeIf { it.persisted } ?: return null
        return try {
            cache.read(scope)?.takeIf { session.local.binding()?.sameScope(scope) == true }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null // Unreadable cache cannot grant access or prevent an online refresh.
        }
    }

    suspend fun openOffline(): Boolean {
        val state = session.state.value
        val reason = (state as? SessionState.Failure)?.reason ?: SessionFailure.SERVER
        val transient = state is SessionState.Failure && reason in setOf(
            SessionFailure.NETWORK,
            SessionFailure.TIMEOUT,
            SessionFailure.SERVER,
            SessionFailure.RATE_LIMIT,
        )
        val profileUnavailable = state == SessionState.Active &&
            (profile.state.value as? ProfileState.Failure)?.transient == true
        val expected = session.snapshot()
        val scope = session.local.binding()?.takeIf { it.persisted }
        val eligible = transient || profileUnavailable
        return if (eligible && scope != null && cached() != null) {
            session.local.openOffline(expected, scope, reason)
        } else {
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(): HomeSummaryResult {
        if (retryAt > System.currentTimeMillis()) return HomeSummaryResult.Failure(SummaryFailure.RATE_LIMIT, retryAt)
        profile.load(force = true)
        val expected = session.snapshot()
        val ready = profile.state.value as? ProfileState.Ready
        return when {
            ready == null -> HomeSummaryResult.Failure(SummaryFailure.CONTRACT)
            ready.profile.storeId == null -> HomeSummaryResult.Failure(SummaryFailure.NO_STORE)
            else -> readStore(expected, ready)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun readStore(expected: SessionSnapshot, ready: ProfileState.Ready): HomeSummaryResult {
        val store = requireNotNull(ready.profile.storeId)
        session.requireAccess(expected)
        val result = repository.read(store)
        session.withCurrentSession(expected) { check(sameScope(ready)) { "Profile changed" } }
        if (result is HomeSummaryResult.Failure) {
            if (result.reason == SummaryFailure.NO_STORE) session.expire(expected)
            if (result.reason == SummaryFailure.RATE_LIMIT) retryAt = result.retryAt
            return result
        }
        result as HomeSummaryResult.Success
        val received = CachedHomeSummary(result.summary, System.currentTimeMillis())
        val scope = session.local.binding()?.takeIf { it.userId == ready.profile.id && it.storeId == store }
        val saved = try {
            scope?.persisted == true && cache.write(scope, received)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        session.withCurrentSession(expected) { check(sameScope(ready)) { "Profile changed" } }
        return HomeSummaryResult.Success(result.summary, received.receivedAt, saved)
    }
    private fun sameScope(expected: ProfileState.Ready): Boolean {
        val actual = profile.state.value as? ProfileState.Ready ?: return false
        return actual.generation == expected.generation && actual.profile.id == expected.profile.id &&
            actual.profile.storeId == expected.profile.storeId
    }
}
