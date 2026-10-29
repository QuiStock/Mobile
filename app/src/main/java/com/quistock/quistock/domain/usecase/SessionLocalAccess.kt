package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.port.AuthRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionLocalAccess internal constructor(
    private val auth: AuthRepository,
    private val lifecycle: SessionLifecycle,
    private val mutex: Mutex,
) {
    fun binding(): SessionBinding? = auth.currentBinding(lifecycle.current.generation)?.takeIf {
        permitsLocalRead() && it.validAt(System.currentTimeMillis())
    }

    private fun permitsLocalRead(): Boolean = when (val state = lifecycle.state.value) {
        SessionState.Active, is SessionState.OfflineReadOnly -> true

        is SessionState.Failure -> state.reason in setOf(
            SessionFailure.NETWORK,
            SessionFailure.TIMEOUT,
            SessionFailure.SERVER,
            SessionFailure.RATE_LIMIT,
        )

        else -> false
    }

    suspend fun rememberProfile(profile: UserProfile, expected: SessionSnapshot) {
        if (lifecycle.current.generation == expected.generation && lifecycle.state.value == SessionState.Active) {
            profile.storeId?.let { auth.bindProfile(expected.generation, profile.id, it) }
        }
    }

    suspend fun openOffline(expected: SessionSnapshot, scope: SessionBinding, reason: SessionFailure): Boolean =
        mutex.withLock {
            val actual = binding()
            val sameSession = lifecycle.current.generation == expected.generation && actual?.sameScope(scope) == true
            val eligible = actual?.let { it.persisted && it.validAt(System.currentTimeMillis()) } == true
            if (sameSession && eligible) {
                lifecycle.state.value = SessionState.OfflineReadOnly(reason)
                true
            } else {
                false
            }
        }

    suspend fun closeOffline(expected: SessionSnapshot) = mutex.withLock {
        if (lifecycle.current.generation == expected.generation &&
            lifecycle.state.value is SessionState.OfflineReadOnly
        ) {
            lifecycle.state.value = SessionState.Failure(SessionFailure.SERVER)
        }
    }

    suspend fun expireOffline(expected: SessionSnapshot) = withContext(NonCancellable) {
        mutex.withLock {
            if (lifecycle.current.generation == expected.generation &&
                lifecycle.state.value is SessionState.OfflineReadOnly &&
                binding() == null
            ) {
                lifecycle.expire()
            }
        }
    }
}
