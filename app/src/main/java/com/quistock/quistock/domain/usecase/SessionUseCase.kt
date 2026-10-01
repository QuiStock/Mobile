package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.AuthTokens
import com.quistock.quistock.domain.model.RefreshToken
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.SecretStorage
import com.quistock.quistock.domain.port.delete
import com.quistock.quistock.domain.port.read
import com.quistock.quistock.domain.port.save
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionUseCase(
    private val auth: AuthRepository,
    private val storage: SecretStorage,
    private val cache: CachedBigNumbersRepository,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state = mutableState.asStateFlow()

    @Volatile
    private var current = SessionSnapshot(0, null)
    private var generation: Long
        get() = current.generation
        set(value) {
            current = SessionSnapshot(value, null)
        }
    private var access: AccessToken?
        get() = current.accessToken
        set(value) {
            current = current.copy(accessToken = value)
        }
    private var refreshToken: RefreshToken? = null
    private var pending: Pair<Long, CompletableDeferred<SessionSnapshot>>? = null

    fun snapshot() = current

    @Suppress("TooGenericExceptionCaught")
    suspend fun install(tokens: AuthTokens): Boolean = mutex.withLock {
        generation++
        try {
            cache.clear()
            save(tokens)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            discardTokens()
            mutableState.value = SessionState.LocalFailure
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun save(tokens: AuthTokens): Boolean {
        access = null
        refreshToken = null
        return try {
            storage.save(tokens.refreshToken)
            storage.save(tokens.accessToken)
            refreshToken = tokens.refreshToken
            current = current.copy(accessToken = tokens.accessToken, revision = current.revision + 1)
            mutableState.value = SessionState.Active
            true
        } catch (e: CancellationException) {
            withContext(NonCancellable) { discardTokens() }
            throw e
        } catch (_: Exception) {
            discardTokens()
            mutableState.value = SessionState.LocalFailure
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun discardTokens() {
        access = null
        refreshToken = null
        // Never publish a token after partial persistence, even if cleanup also fails.
        try {
            storage.delete<AccessToken>()
        } catch (_: Exception) { /* In-memory session remains blocked. */ }
        try {
            storage.delete<RefreshToken>()
        } catch (_: Exception) { /* A later login may repair storage. */ }
    }

    suspend fun restore(): SessionState {
        val entryGeneration = generation
        val read = readRecoveryToken()
        val expected = mutex.withLock {
            when {
                entryGeneration != generation -> null
                read.isFailure -> {
                    mutableState.value = SessionState.LocalFailure
                    null
                }
                else -> {
                    refreshToken = read.getOrNull()
                    generation++
                    if (refreshToken == null) {
                        mutableState.value = SessionState.SignedOut
                        null
                    } else {
                        snapshot()
                    }
                }
            }
        }
        if (expected != null) refresh(expected)
        return mutableState.value
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun readRecoveryToken(): Result<RefreshToken?> = try {
        Result.success(storage.read<RefreshToken>())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun refresh(expected: SessionSnapshot): SessionSnapshot {
        var owner = false
        var token: RefreshToken? = null
        val task = mutex.withLock {
            if (expected.generation != generation ||
                (access != null && current.revision != expected.revision)
            ) return snapshot()
            pending?.takeIf { it.first == generation }?.second ?: CompletableDeferred<SessionSnapshot>().also {
                pending = generation to it
                token = refreshToken
                owner = true
            }
        }
        if (owner) {
            try {
                val result = requestRefresh(auth, token)
                mutex.withLock {
                    if (generation == expected.generation) applyRefresh(result)
                    task.complete(snapshot())
                }
            } catch (e: CancellationException) {
                task.cancel(e)
                throw e
            } catch (_: Exception) {
                // Local failure handling already blocks token publication or expires the generation.
                task.complete(snapshot())
            } finally {
                withContext(NonCancellable) {
                    mutex.withLock { if (pending?.second === task) pending = null }
                }
            }
        }
        return task.await()
    }

    private suspend fun applyRefresh(result: AuthRefreshResult) {
        when (result) {
            is AuthResult.Success -> save(result.tokens)
            AuthRefreshError.InvalidToken -> expireLocked()
            AuthRefreshError.NetworkError -> mutableState.value = SessionState.Failure(SessionFailure.NETWORK)
            AuthRefreshError.Timeout -> mutableState.value = SessionState.Failure(SessionFailure.TIMEOUT)
            AuthRefreshError.ServerError -> mutableState.value = SessionState.Failure(SessionFailure.SERVER)
            AuthRefreshError.UnexpectedError -> mutableState.value = SessionState.Failure(SessionFailure.UNEXPECTED)
        }
    }

    suspend fun expire(expected: SessionSnapshot) = mutex.withLock {
        if (generation == expected.generation) expireLocked()
    }

    private suspend fun expireLocked() {
        generation++
        discardTokens()
        try {
            cache.clear()
        } finally {
            mutableState.value = SessionState.Expired
        }
    }

    suspend fun <T> withCurrentSession(expected: SessionSnapshot, action: suspend () -> T): T = mutex.withLock {
        if (expected.generation != generation || access == null || state.value != SessionState.Active) {
            throw SessionException((state.value as? SessionState.Failure)?.reason)
        }
        action()
    }

    suspend fun requireAccess(expected: SessionSnapshot): SessionSnapshot = mutex.withLock {
        val validated = snapshot()
        if (expected.generation != validated.generation || validated.accessToken == null ||
            state.value != SessionState.Active
        ) {
            throw SessionException((state.value as? SessionState.Failure)?.reason)
        }
        validated
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun requestRefresh(auth: AuthRepository, token: RefreshToken?): AuthRefreshResult = try {
    if (token == null) AuthRefreshError.InvalidToken else auth.refresh(token)
} catch (e: CancellationException) {
    throw e
} catch (_: java.net.SocketTimeoutException) {
    AuthRefreshError.Timeout
} catch (_: java.io.IOException) {
    AuthRefreshError.NetworkError
} catch (_: Exception) {
    AuthRefreshError.UnexpectedError
}
