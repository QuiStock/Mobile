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
class AuthenticatedSessionRule(private val role: UserRole = UserRole.EMPLOYEE) : TestWatcher() {
    private lateinit var previous: SessionUseCase
    private lateinit var profileDependencies: ProfileTestDependencies
    private lateinit var previousAlerts: PriorityAlertsRepository

    override fun starting(description: Description) {
        val koin = GlobalContext.get()
        previous = koin.get()
        val auth = mockk<AuthRepository>(relaxed = true)
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.restore(any()) } returns StoredSession.AVAILABLE
        coEvery { auth.refresh(any()) } returns AuthResult.Success
        val session = SessionUseCase(auth)
        runBlocking { session.restore() }
        profileDependencies = ProfileTestDependencies(session, role)
        previousAlerts = koin.get()
        val alerts: PriorityAlertsRepository = object : PriorityAlertsRepository {
            override suspend fun read(storeId: Long, storeCode: String?) = AlertProductsResult.Success(emptyList(), 0)
        }
        koin.declare(alerts, allowOverride = true)
        koin.declare(session, allowOverride = true)
        profileDependencies.install()
    }

    override fun finished(description: Description) {
        profileDependencies.restore()
        GlobalContext.get().declare(previousAlerts, allowOverride = true)
        GlobalContext.get().declare(previous, allowOverride = true)
    }
}
