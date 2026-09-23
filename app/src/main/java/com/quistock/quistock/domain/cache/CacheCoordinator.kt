package com.quistock.quistock.domain.cache

import com.quistock.quistock.domain.port.Clock
import com.quistock.quistock.domain.port.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.IOException
import kotlin.time.Duration
import kotlin.time.Instant

abstract class CacheCoordinator<T>(
    private val remote: RemoteSource<T>,
    private val local: LocalSource<T>,
    private val clock: Clock,
    private val logger: Logger,
    private val cacheLifetime: Duration? = null,
) {
    private fun resolveExpiration(): Instant = cacheLifetime
        ?.let { clock.now() + it }
        ?: clock.nextMidnight()

    fun load(): Flow<CacheLoadState<T>> = flow {
        val now = clock.now()
        val cached = local.read()

        val fresh = try {
            remote.fetch()
        } catch (e: IOException) {
            logger.warn(msg = "Failed to fetch remote", exception = e)
            if (cached == null) {
                emit(CacheLoadState.Unavailable)
            } else {
                val isExpired = cached.expiresAt <= now
                emit(CacheLoadState.Data(value = cached.value, staleWarning = isExpired))
            }
            return@flow
        }

        val freshCache = CachedValue(value = fresh, savedAt = now, expiresAt = resolveExpiration())
        local.save(freshCache)
        emit(CacheLoadState.Data(value = fresh))
    }
}
