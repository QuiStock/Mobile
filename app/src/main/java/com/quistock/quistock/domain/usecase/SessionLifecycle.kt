package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.UserPreferences
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Mutable transitions called under SessionUseCase's mutex, except independent logout cleanup. */
internal class SessionLifecycle(private val auth: AuthRepository, private val preferences: UserPreferences?) {
    val state = MutableStateFlow<SessionState>(SessionState.Restoring)

    @Volatile var current = SessionSnapshot(0, false)

    fun activate() {
        current = current.copy(hasAccess = true, revision = current.revision + 1)
        state.value = SessionState.Active
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun localFailure() {
        current = current.copy(hasAccess = false)
        try {
            auth.clear(current.generation)
        } catch (_: Exception) { /* Access remains blocked by state. */ }
        state.value = SessionState.LocalFailure
    }

    suspend fun expire() = withContext(NonCancellable) {
        val previous = current.generation
        current = SessionSnapshot(previous + 1, false)
        val cleared = runCatching { auth.clear(previous) }
        // Advance custody even when deletion fails, so an old response cannot recreate the credential.
        val fenced = runCatching { auth.begin(current.generation) }
        state.value = if (cleared.isSuccess && fenced.isSuccess) SessionState.Expired else SessionState.LocalFailure
    }

    suspend fun clearLogoutData(generation: Long): Boolean {
        // Advance custody before deleting disk data so stale commits cannot repopulate it.
        val fenced = runCatching { auth.begin(generation) }.isSuccess
        val cleared = runCatching { auth.clear(generation) }.isSuccess
        val identity = runCatching { preferences?.clearUserId() }.isSuccess
        return fenced && cleared && identity
    }
}

internal val SessionState.blocksLogout: Boolean
    get() = this == SessionState.LoggingOut || this is SessionState.LogoutFailed

internal val SessionState.canRefresh: Boolean
    get() = when (this) {
        SessionState.Restoring, SessionState.Active, is SessionState.Failure, is SessionState.OfflineReadOnly -> true
        else -> false
    }
