package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ProfileUseCase(
    private val repository: ProfileRepository,
    private val session: SessionUseCase,
    private val logger: Logger? = null,
) {
    private val logContext = mapOf("operation" to "core_profile", "stage" to "profile_state")
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<ProfileState>(ProfileState.Empty)
    val state = mutableState.asStateFlow()

    @Volatile private var generation: Long? = null
    private var pending: Pair<Long, CompletableDeferred<Unit>>? = null

    suspend fun publishEdit(profile: UserProfile, expected: SessionSnapshot, invalidatePhoto: () -> Unit): Boolean =
        session.withCurrentSession(expected) {
            val current = state.value as? ProfileState.Ready
            val valid = current != null && current.generation == expected.generation && current.profile.id == profile.id
            val accepted = valid && profile.rejection() == null
            if (accepted) {
                invalidatePhoto()
                mutableState.value = ProfileState.Ready(expected.generation, profile)
            }
            accepted
        }

    fun clear() {
        generation = null
        mutableState.value = ProfileState.Empty
        logger?.debug("Core profile cache cleared", logContext)
    }

    suspend fun load(force: Boolean = false) {
        val expected = session.snapshot()
        var owner = false
        val task = try {
            mutex.withLock {
                if (session.state.value != SessionState.Active) return@withLock null
                session.withCurrentSession(expected) {
                    val cached = state.value as? ProfileState.Ready
                    if (!force && cached?.generation == expected.generation) {
                        logger?.debug("Core profile cache reused", logContext)
                        null
                    } else {
                        pending?.takeIf { it.first == expected.generation }?.second
                            ?: CompletableDeferred<Unit>().also {
                                pending = expected.generation to it
                                generation = expected.generation
                                if (cached?.generation != expected.generation) mutableState.value = ProfileState.Loading
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
            logger?.debug("Core profile request shared", logContext)
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
            if (result is ProfileResult.Success && result.profile.rejection() == null) {
                session.rememberProfile(result.profile, expected)
            }
            var rejection: ProfileState.Rejected? = null
            session.withCurrentSession(expected) {
                if (generation != expected.generation) return@withCurrentSession
                when (result) {
                    is ProfileResult.Success -> rejection = acceptProfile(result.profile, expected)

                    ProfileResult.NoActiveStore -> rejection = ProfileState.Rejected(ProfileRejection.NO_STORE)

                    ProfileResult.Forbidden -> {
                        logger?.info("Core profile rejected", logContext + ("reason" to "forbidden"))
                        rejection = ProfileState.Rejected(ProfileRejection.FORBIDDEN)
                    }

                    is ProfileResult.Failure -> if (state.value !is ProfileState.Ready) {
                        mutableState.value = ProfileState.Failure(result.retryAfterMillis)
                    }
                }
                rejection?.let { mutableState.value = it }
            }
            if (rejection != null) session.expire(expected)
        } catch (error: CancellationException) {
            logger?.debug("Core profile load cancelled", logContext)
            throw error
        } catch (error: Exception) {
            val current = generation == expected.generation && session.snapshot().generation == expected.generation
            logger?.info(
                "Core profile result unavailable",
                logContext + mapOf(
                    "failure" to if (error is SessionException) "session_unavailable" else "unexpected",
                    "result_discarded" to !current,
                ),
            )
            if (current) mutableState.value = ProfileState.Failure()
        }
    }

    private fun acceptProfile(profile: UserProfile, expected: SessionSnapshot): ProfileState.Rejected? {
        val reason = profile.rejection()
        if (reason != null) {
            logger?.info("Core profile rejected", logContext + ("reason" to reason.name.lowercase()))
        }
        return when (reason) {
            null -> {
                mutableState.value = ProfileState.Ready(expected.generation, profile)
                logger?.debug("Core profile loaded", logContext)
                null
            }

            ProfileRejection.INVALID -> {
                mutableState.value = ProfileState.Failure()
                null
            }

            else -> ProfileState.Rejected(reason, profile.role)
        }
    }
}
