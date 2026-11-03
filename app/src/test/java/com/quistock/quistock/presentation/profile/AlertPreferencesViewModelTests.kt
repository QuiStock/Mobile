package com.quistock.quistock.presentation.profile

import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.RoleAlertType
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.usecase.RoleAlertsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertPreferencesViewModelTests {
    @get:Rule val main = MainDispatcherRule()
    private val alerts = mockk<RoleAlertsUseCase>(relaxed = true)
    private val enabled = mapOf(RoleAlertType.NEAR_EXPIRY to true, RoleAlertType.PENDING_APPROVAL to true)

    @Test
    fun `loads role switches, saves changes and checks again when enabling`() = runTest {
        coEvery { alerts.settings() } returns RoleAlertsUseCase.Settings(UserRole.MANAGER, enabled)
        coEvery { alerts.setEnabled(any(), any()) } returns true
        val model = AlertPreferencesViewModel(alerts)
        model.load()
        runCurrent()
        assertEquals(enabled, model.state.value)

        model.set(RoleAlertType.PENDING_APPROVAL, false)
        model.set(RoleAlertType.PENDING_APPROVAL, false)
        model.set(RoleAlertType.REQUEST_APPROVED, false)
        runCurrent()
        assertEquals(false, model.state.value[RoleAlertType.PENDING_APPROVAL])
        coVerify(exactly = 1) { alerts.setEnabled(any(), any()) }
        coVerify(exactly = 0) { alerts.check() }

        model.set(RoleAlertType.PENDING_APPROVAL, true)
        runCurrent()
        coVerify(exactly = 1) { alerts.check() }
    }

    @Test
    fun `rejected save reloads the stored state and no profile hides the section`() = runTest {
        coEvery { alerts.settings() } returnsMany listOf(
            RoleAlertsUseCase.Settings(UserRole.MANAGER, enabled),
            null,
        )
        coEvery { alerts.setEnabled(any(), any()) } returns false
        val model = AlertPreferencesViewModel(alerts)
        model.load()
        runCurrent()
        model.set(RoleAlertType.NEAR_EXPIRY, false)
        runCurrent()
        assertEquals(emptyMap<RoleAlertType, Boolean>(), model.state.value)
    }
}
