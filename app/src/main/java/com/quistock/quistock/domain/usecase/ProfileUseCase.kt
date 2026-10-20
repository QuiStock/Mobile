package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ProfileUseCase(private val repository: ProfileRepository, private val session: SessionUseCase) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<ProfileState>(ProfileState.Empty)
    val state = mutableState.asStateFlow()
    @Volatile private var generation: Long? = null
    private var pending: Pair<Long, CompletableDeferred<Unit>>? = null

    fun clear() {
        generation = null
        mutableState.value = ProfileState.Empty
    }

    suspend fun load() {
        val expected = session.snapshot()
        var owner = false
        val task = try {
            mutex.withLock {
                if (session.state.value != SessionState.Active) return@withLock null
                session.withCurrentSession(expected) {
                    val cached = state.value as? ProfileState.Ready
                    if (cached?.generation == expected.generation) {
                        null
                    } else {
                        pending?.takeIf { it.first == expected.generation }?.second
                            ?: CompletableDeferred<Unit>().also {
                                pending = expected.generation to it
                                generation = expected.generation
                                mutableState.value = ProfileState.Loading
                                owner = true
                            }
                    }
                }
            }
        } catch (_: SessionException) {
            null
        } ?: return
        if (owner) {
            completeLoad(expected, task)
        } else {
            task.await()
        }
    }

    private suspend fun completeLoad(expected: SessionSnapshot, task: CompletableDeferred<Unit>) {
        try {
            read(expected)
            task.complete(Unit)
        } catch (error: CancellationException) {
            if (generation == expected.generation && state.value == ProfileState.Loading) {
                mutableState.value = ProfileState.Empty
            }
            task.cancel(error)
            throw error
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { if (pending?.second === task) pending = null }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(expected: SessionSnapshot) {
        try {
            val result = repository.read()
            var rejection: ProfileState.Rejected? = null
            session.withCurrentSession(expected) {
                if (generation != expected.generation) return@withCurrentSession
                when (result) {
                    is ProfileResult.Success -> {
                        val reason = result.profile.rejection()
                        if (reason == null) {
                            mutableState.value = ProfileState.Ready(expected.generation, result.profile)
                        } else if (reason == ProfileRejection.INVALID) {
                            mutableState.value = ProfileState.Failure()
                        } else {
                            rejection = ProfileState.Rejected(reason, result.profile.role)
                        }
                    }
                    ProfileResult.Forbidden -> rejection = ProfileState.Rejected(ProfileRejection.FORBIDDEN)
                    is ProfileResult.Failure -> mutableState.value = ProfileState.Failure(result.retryAfterMillis)
                }
                rejection?.let { mutableState.value = it }
            }
            if (rejection != null) session.expire(expected)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (generation == expected.generation && session.snapshot().generation == expected.generation) {
                mutableState.value = ProfileState.Failure()
            }
        }
    }
}
