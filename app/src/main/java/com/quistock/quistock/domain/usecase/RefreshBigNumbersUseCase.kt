package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.model.RefreshResult
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.Clock
import com.quistock.quistock.domain.port.ErrorReporter
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.RemoteBigNumbersRepository
import java.io.IOException

class RefreshBigNumbersUseCase(
    private val remote: RemoteBigNumbersRepository,
    private val local: CachedBigNumbersRepository,
    private val clock: Clock,
    private val logger: Logger,
    private val errorReporter: ErrorReporter,
    private val session: SessionUseCase? = null,
) {
    private val logContext = mapOf("operation" to "Big Numbers refresh")

    suspend operator fun invoke(): RefreshResult<BigNumbers> {
        val expected = session?.snapshot()
        val cached = local.read()
        if (session?.state?.value is SessionState.Failure) {
            return if (cached == null) RefreshResult.Failed else RefreshResult.Stale(cached)
        }
        return if (cached != null && clock.midnightOfDay(cached.createdAt) == clock.midnightOfDay(clock.now())) {
            logger.info(msg = "Using up-to-date cached Big Numbers", context = logContext)
            RefreshResult.UpToDate(cached)
        } else {
            fetchOrUseStaleCache(cached, expected)
        }
    }

    private suspend fun fetchOrUseStaleCache(
        cached: BigNumbers?,
        expected: SessionSnapshot?,
    ): RefreshResult<BigNumbers> = try {
        val fresh = remote.fetch()
        logger.info(msg = "Using up-to-date fetched Big Numbers and updating cache", context = logContext)
        if (session != null && expected != null) {
            session.withCurrentSession(expected) { saveToCache(fresh) }
        } else {
            saveToCache(fresh)
        }
        RefreshResult.UpToDate(fresh)
    } catch (e: SessionException) {
        if (e.reason == null) throw e
        if (cached == null) RefreshResult.Failed else RefreshResult.Stale(cached)
    } catch (e: IOException) {
        logger.warn(msg = "Failed to fetch Big Numbers", throwable = e, context = logContext)
        if (cached == null) {
            RefreshResult.Failed
        } else {
            logger.warn(
                msg = "Using stale cached Big Numbers",
                context = logContext + ("last_updated" to cached.createdAt),
            )
            RefreshResult.Stale(cached)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun saveToCache(fresh: BigNumbers) {
        try {
            local.save(fresh)
        } catch (e: Exception) {
            errorReporter.record(throwable = e, context = logContext)
            logger.error(msg = "Failed to save fresh value in cache", throwable = e, context = logContext)
        }
    }
}
