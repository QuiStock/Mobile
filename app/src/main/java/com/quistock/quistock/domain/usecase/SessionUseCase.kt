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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionUseCase(private val auth: AuthRepository) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state = mutableState.asStateFlow()

    @Volatile private var current = SessionSnapshot(0, false)
    private var pending: Pair<Long, CompletableDeferred<SessionSnapshot>>? = null

    val retryDelayMillis get() = auth.retryDelayMillis()

    fun snapshot(): SessionSnapshot {
        val captured = current
        return captured.copy(hasAccess = auth.hasAccess(captured.generation))
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun login(email: String, password: String): LoginResult {
        val expected = mutex.withLock {
            current = SessionSnapshot(current.generation + 1, false)
            try {
                auth.begin(current.generation)
                auth.clear(current.generation)
                mutableState.value = SessionState.SignedOut
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = SessionState.LocalFailure
                return LoginError.UnexpectedError
            }
            current
        }
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
                    activate()
                    result
                } else if (result == LoginError.UnexpectedError || result == AuthResult.Success) {
                    localFailure()
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
                    StoredSession.EXPIRED -> expireLocked()
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
            if (expected.generation != current.generation ||
                (auth.hasAccess(current.generation) && current.revision != expected.revision)
            ) {
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
                        if (current.generation == expected.generation) localFailure()
                        task.complete(snapshot())
                    }
                }
            } finally {
                withContext(NonCancellable) { mutex.withLock { if (pending?.second === task) pending = null } }
            }
        }
        return task.await()
    }

    private suspend fun applyRefresh(result: AuthRefreshResult) {
        when (result) {
            AuthResult.Success -> if (auth.hasAccess(current.generation)) activate() else localFailure()
            AuthRefreshError.InvalidToken -> expireLocked()
            AuthRefreshError.UnexpectedError -> localFailure()
            AuthRefreshError.ProtocolError -> mutableState.value = SessionState.Failure(SessionFailure.PROTOCOL)
            AuthRefreshError.NetworkError -> mutableState.value = SessionState.Failure(SessionFailure.NETWORK)
            AuthRefreshError.Timeout -> mutableState.value = SessionState.Failure(SessionFailure.TIMEOUT)
            AuthRefreshError.ServerError -> mutableState.value = SessionState.Failure(SessionFailure.SERVER)
            AuthRefreshError.RateLimited -> mutableState.value = SessionState.Failure(SessionFailure.RATE_LIMIT)
        }
    }

    private fun activate() {
        current = current.copy(hasAccess = true, revision = current.revision + 1)
        mutableState.value = SessionState.Active
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun localFailure() {
        current = current.copy(hasAccess = false)
        try {
            auth.clear(current.generation)
        } catch (_: Exception) { /* Access remains blocked by state. */ }
        mutableState.value = SessionState.LocalFailure
    }

    suspend fun expire(expected: SessionSnapshot) = withContext(NonCancellable) {
        mutex.withLock { if (current.generation == expected.generation) expireLocked() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun expireLocked() = withContext(NonCancellable) {
        val previous = current.generation
        current = SessionSnapshot(previous + 1, false)
        val cleared = runCatching { auth.clear(previous) }
        // Advance custody even when deletion fails, so an old response cannot recreate the credential.
        val fenced = runCatching { auth.begin(current.generation) }
        mutableState.value = if (cleared.isSuccess && fenced.isSuccess) {
            SessionState.Expired
        } else {
            SessionState.LocalFailure
        }
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
