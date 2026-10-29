package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.StoredSession
import com.quistock.quistock.domain.port.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionUseCase(private val auth: AuthRepository, preferences: UserPreferences? = null) {
    private val mutex = Mutex()
    private val lifecycle = SessionLifecycle(auth, preferences)
    val local = SessionLocalAccess(auth, lifecycle, mutex)
    private val mutableState get() = lifecycle.state
    val state = mutableState.asStateFlow()

    private var current: SessionSnapshot
        get() = lifecycle.current
        set(value) {
            lifecycle.current = value
        }

    private var pending: Pair<Long, CompletableDeferred<SessionSnapshot>>? = null
    private var logoutPending: CompletableDeferred<Unit>? = null

    private val logoutBlocked get() = state.value.blocksLogout

    @Suppress("TooGenericExceptionCaught")
    suspend fun logout() = withContext(NonCancellable) {
        var owner = false
        val task = mutex.withLock {
            logoutPending ?: CompletableDeferred<Unit>().also {
                logoutPending = it
                owner = true
            }
        }
        if (!owner) {
            task.await()
            return@withContext
        }
        try {
            val operation = mutex.withLock {
                if (state.value is SessionState.LoggedOut || state.value is SessionState.LogoutFailed) {
                    return@withLock null
                }
                val previous = current.generation
                mutableState.value = SessionState.LoggingOut
                val captured = runCatching { auth.prepareLogout(previous) }.getOrNull()
                current = SessionSnapshot(previous + 1, false)
                captured
            }
            if (state.value != SessionState.LoggingOut) return@withContext
            val generation = current.generation
            coroutineScope {
                // Both operations start independently: disk revocation never waits for the network.
                val local = async { lifecycle.clearLogoutData(generation) }
                val remote = async { runCatching { operation?.execute() == true }.getOrDefault(false) }
                val cleared = local.await()
                val confirmed = remote.await()
                mutex.withLock {
                    if (current.generation == generation) {
                        mutableState.value = if (cleared) {
                            SessionState.LoggedOut(confirmed)
                        } else {
                            SessionState.LogoutFailed(confirmed)
                        }
                    }
                }
            }
        } finally {
            mutex.withLock {
                task.complete(Unit)
                if (logoutPending === task) logoutPending = null
            }
        }
    }

    suspend fun retryLogoutCleanup() = withContext(NonCancellable) {
        val retry = mutex.withLock {
            val failure = state.value as? SessionState.LogoutFailed ?: return@withLock null
            mutableState.value = SessionState.LoggingOut
            current.generation to failure.remoteConfirmed
        } ?: return@withContext
        val cleared = lifecycle.clearLogoutData(retry.first)
        mutex.withLock {
            if (current.generation == retry.first) {
                mutableState.value = if (cleared) {
                    SessionState.LoggedOut(retry.second)
                } else {
                    SessionState.LogoutFailed(retry.second)
                }
            }
        }
    }

    val retryDelayMillis get() = auth.retryDelayMillis()

    fun snapshot(): SessionSnapshot {
        val captured = current
        return captured.copy(hasAccess = auth.hasAccess(captured.generation))
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun login(email: String, password: String): LoginResult {
        val expected = mutex.withLock {
            if (logoutBlocked) return@withLock null
            current = SessionSnapshot(current.generation + 1, false)
            try {
                auth.begin(current.generation)
                auth.clear(current.generation)
                mutableState.value = SessionState.SignedOut
                current
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = SessionState.LocalFailure
                null
            }
        } ?: return LoginError.UnexpectedError
        val result = try {
            auth.login(email, password, expected.generation)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LoginError.UnexpectedError
        }
        return withContext(NonCancellable) {
            mutex.withLock {
                if (current.generation != expected.generation) return@withLock LoginError.UnexpectedError
                if (result == AuthResult.Success && auth.hasAccess(expected.generation)) {
                    lifecycle.activate()
                    result
                } else if (result == LoginError.UnexpectedError || result == AuthResult.Success) {
                    lifecycle.localFailure()
                    LoginError.UnexpectedError
                } else {
                    result
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun restore(): SessionState {
        val expected = mutex.withLock {
            if (state.value != SessionState.Restoring) return state.value
            current = SessionSnapshot(current.generation + 1, false)
            try {
                auth.begin(current.generation)
                when (auth.restore(current.generation)) {
                    StoredSession.MISSING -> mutableState.value = SessionState.SignedOut
                    StoredSession.EXPIRED -> lifecycle.expire()
                    StoredSession.AVAILABLE -> return@withLock current
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = SessionState.LocalFailure
            }
            null
        }
        if (expected != null) refresh(expected)
        return state.value
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun refresh(expected: SessionSnapshot): SessionSnapshot {
        var owner = false
        val task = mutex.withLock {
            if (!state.value.canRefresh || refreshObsolete(expected)) {
                return snapshot()
            }
            pending?.takeIf { it.first == current.generation }?.second ?: CompletableDeferred<SessionSnapshot>().also {
                pending = current.generation to it
                owner = true
            }
        }
        if (owner) {
            try {
                val result = auth.refresh(expected.generation)
                withContext(NonCancellable) {
                    mutex.withLock {
                        if (current.generation == expected.generation) applyRefresh(result)
                        task.complete(snapshot())
                    }
                }
            } catch (e: CancellationException) {
                task.cancel(e)
                throw e
            } catch (_: Exception) {
                withContext(NonCancellable) {
                    mutex.withLock {
                        if (current.generation == expected.generation) lifecycle.localFailure()
                        task.complete(snapshot())
                    }
                }
            } finally {
                withContext(NonCancellable) { mutex.withLock { if (pending?.second === task) pending = null } }
            }
        }
        return task.await()
    }

    private fun refreshObsolete(expected: SessionSnapshot): Boolean = expected.generation != current.generation ||
        (auth.hasAccess(current.generation) && current.revision != expected.revision)

    private suspend fun applyRefresh(result: AuthRefreshResult) {
        when (result) {
            AuthResult.Success -> if (auth.hasAccess(current.generation)) {
                lifecycle.activate()
            } else {
                lifecycle.localFailure()
            }

            AuthRefreshError.InvalidToken -> lifecycle.expire()

            AuthRefreshError.UnexpectedError -> lifecycle.localFailure()

            AuthRefreshError.ProtocolError -> mutableState.value = SessionState.Failure(SessionFailure.PROTOCOL)

            AuthRefreshError.NetworkError -> mutableState.value = SessionState.Failure(SessionFailure.NETWORK)

            AuthRefreshError.Timeout -> mutableState.value = SessionState.Failure(SessionFailure.TIMEOUT)

            AuthRefreshError.ServerError -> mutableState.value = SessionState.Failure(SessionFailure.SERVER)

            AuthRefreshError.RateLimited -> mutableState.value = SessionState.Failure(SessionFailure.RATE_LIMIT)
        }
    }

    suspend fun expire(expected: SessionSnapshot) = withContext(NonCancellable) {
        mutex.withLock { if (current.generation == expected.generation && !logoutBlocked) lifecycle.expire() }
    }

    suspend fun <T> withCurrentSession(expected: SessionSnapshot, action: suspend () -> T): T = mutex.withLock {
        if (current.generation != expected.generation || !auth.hasAccess(current.generation) ||
            state.value != SessionState.Active
        ) {
            throw SessionException((state.value as? SessionState.Failure)?.reason)
        }
        action()
    }
    suspend fun requireAccess(expected: SessionSnapshot): SessionSnapshot = withCurrentSession(expected) { snapshot() }
}
