package com.quistock.quistock

import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.port.StoredSession
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.GlobalContext

/** Feature UI tests own their session instead of restoring credentials from the device. */
class AuthenticatedSessionRule(
    private val role: UserRole = UserRole.EMPLOYEE,
    private val auth: AuthRepository = mockk(relaxed = true),
) : TestWatcher() {
    private lateinit var previous: SessionUseCase
    private lateinit var profileDependencies: ProfileTestDependencies
    private lateinit var previousSummary: com.quistock.quistock.domain.usecase.GetHomeSummaryUseCase
    private lateinit var active: SessionUseCase
    private val scope = com.quistock.quistock.domain.model.SessionBinding(
        "fixture-session",
        "1",
        1,
        System.currentTimeMillis() + 600_000,
    )
    private lateinit var previousAlerts: PriorityAlertsRepository

    override fun starting(description: Description) {
        val koin = GlobalContext.get()
        previous = koin.get()
        previousSummary = koin.get()
        every { auth.hasAccess(any()) } returns true
        every { auth.currentBinding(any()) } returns scope
        coEvery { auth.bindProfile(any(), any(), any()) } returns scope
        coEvery { auth.restore(any()) } returns StoredSession.AVAILABLE
        coEvery { auth.refresh(any()) } returns AuthResult.Success
        val session = SessionUseCase(auth)
        active = session
        runBlocking { session.restore() }
        profileDependencies = ProfileTestDependencies(session, role)
        previousAlerts = koin.get()
        val alerts: PriorityAlertsRepository = object : PriorityAlertsRepository {
            override suspend fun read(storeId: Long, storeCode: String?) = AlertProductsResult.Success(emptyList(), 0)
        }
        koin.declare(alerts, allowOverride = true)
        koin.declare(session, allowOverride = true)
        profileDependencies.install()
        val summary = com.quistock.quistock.domain.model.HomeSummary(4, 2, 3, 5)
        val saved = com.quistock.quistock.domain.model.CachedHomeSummary(summary, System.currentTimeMillis())
        val remote = object : com.quistock.quistock.domain.port.HomeSummaryRepository {
            override suspend fun read(storeId: Long) =
                com.quistock.quistock.domain.model.HomeSummaryResult.Success(summary)
        }
        val cache = object : com.quistock.quistock.domain.port.HomeSummaryCache {
            override suspend fun read(scope: com.quistock.quistock.domain.model.SessionBinding) = saved
            override suspend fun write(
                scope: com.quistock.quistock.domain.model.SessionBinding,
                summary: com.quistock.quistock.domain.model.CachedHomeSummary,
            ) = true
        }
        koin.declare(
            com.quistock.quistock.domain.usecase.GetHomeSummaryUseCase(remote, cache, session, koin.get()),
            allowOverride = true,
        )
    }

    fun goOffline() = runBlocking {
        active.local.openOffline(active.snapshot(), scope, com.quistock.quistock.domain.model.SessionFailure.NETWORK)
    }

    override fun finished(description: Description) {
        GlobalContext.get().declare(previousSummary, allowOverride = true)
        profileDependencies.restore()
        GlobalContext.get().declare(previousAlerts, allowOverride = true)
        GlobalContext.get().declare(previous, allowOverride = true)
    }
}
