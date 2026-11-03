package com.quistock.quistock.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quistock.quistock.domain.port.AlertScheduler
import com.quistock.quistock.domain.usecase.RoleAlertsUseCase
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** One unique periodic check at Android's minimum interval, only with network. */
class WorkManagerAlertScheduler(private val context: Context) : AlertScheduler {
    override fun start() {
        val request = PeriodicWorkRequestBuilder<RoleAlertsWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun stop() {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "quistock_role_alerts"
        const val PERIOD_MINUTES = 15L
    }
}

/** A failed cycle simply waits for the next period; Core is never retried aggressively. */
class RoleAlertsWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {
    private val alerts: RoleAlertsUseCase by inject()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        try {
            alerts.runBackground()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Unit
        }
        return Result.success()
    }
}
