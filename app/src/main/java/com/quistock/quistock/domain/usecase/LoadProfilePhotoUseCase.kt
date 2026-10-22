package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.port.ProfilePhotoRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LoadProfilePhotoUseCase(
    private val repository: ProfilePhotoRepository,
    private val profile: ProfileUseCase,
    private val session: SessionUseCase,
) {
    private val mutex = Mutex()
    private var cachedGeneration: Long? = null
    private var cachedUrl: String? = null
    private var cachedBytes: ByteArray? = null

    fun clear() {
        cachedGeneration = null
        cachedUrl = null
        cachedBytes = null
    }

    suspend operator fun invoke(): ByteArray? = mutex.withLock {
        val ready = profile.state.value as? ProfileState.Ready ?: return@withLock null
        val expected = session.snapshot()
        if (ready.generation != expected.generation) return@withLock null
        val url = ready.profile.photoUrl?.takeIf { it.isNotBlank() } ?: return@withLock null
        if (cachedGeneration == ready.generation && cachedUrl == url) return@withLock cachedBytes
        val bytes = repository.read(url)
        session.withCurrentSession(expected) {
            if (profile.state.value !== ready) return@withCurrentSession null
            cachedGeneration = ready.generation
            cachedUrl = url
            cachedBytes = bytes
            bytes
        }
    }
}
