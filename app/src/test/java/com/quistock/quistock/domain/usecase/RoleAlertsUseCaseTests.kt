package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertMemory
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.AlertTarget
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.RoleAlert
import com.quistock.quistock.domain.model.RoleAlertType
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.AlertMemoryStore
import com.quistock.quistock.domain.port.AlertNotifier
import com.quistock.quistock.domain.port.AlertScheduler
import com.quistock.quistock.domain.port.InterferenceRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RoleAlertsUseCaseTests {
    private val products = mockk<PriorityAlertsRepository>()
    private val actions = mockk<InterferenceRepository>()
    private val session = mockk<SessionUseCase>(relaxed = true)
    private val profile = mockk<ProfileUseCase>(relaxed = true)
    private val scheduler = mockk<AlertScheduler>(relaxed = true)
    private val memory = MemoryStore()
    private val notifier = RecordingNotifier()
    private val expected = SessionSnapshot(1, true)
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Active)
    private val profileState = MutableStateFlow<ProfileState>(ProfileState.Empty)

    // 2026-11-02 12:00 UTC is 09:00 in Sao Paulo.
    private var clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private fun alerts() = RoleAlertsUseCase(products, actions, profile, session, memory, notifier, scheduler, clock)

    private fun signIn(role: UserRole, id: String = "7") {
        every { session.snapshot() } returns expected
        every { session.state } returns sessionState
        every { profile.state } returns profileState
        profileState.value = ProfileState.Ready(
            1,
            UserProfile(id, "Fixture", "fixture@example.test", role, UserStatus.ACTIVE, null, 17, "Store"),
        )
        coEvery { products.read(17, null) } returns AlertProductsResult.Success(emptyList(), 0)
        coEvery { actions.list(17, null, expected) } returns ActionListResult.Loaded(emptyList())
    }

    private fun product(id: String, vararg risks: CreationRisk) =
        AlertProduct(id, "Produto $id", "Cat", BigDecimal.ONE, 3, StockFlow.LOW, "flow", risks = risks.toSet())

    private fun action(id: String, status: ActionStatus, type: ActionType = ActionType.PROMOTION, store: Long = 17) =
        InterferenceAction(id, "flow", "p$id", store, type, status, ActionDraft(), productName = "Produto $id")

    private fun catalog(vararg items: AlertProduct) {
        coEvery { products.read(17, null) } returns AlertProductsResult.Success(items.toList(), 0)
    }

    private fun store(vararg items: InterferenceAction) {
        coEvery { actions.list(17, null, expected) } returns ActionListResult.Loaded(items.toList())
    }

    @Test
    fun `RA-01 near expiry alerts employee and manager, never expired products`() = runTest {
        listOf(UserRole.EMPLOYEE, UserRole.MANAGER).forEach { role ->
            notifier.posted.clear()
            memory.data.clear()
            signIn(role)
            catalog(
                product("a", CreationRisk.NEAR_EXPIRY),
                product("b", CreationRisk.NEAR_EXPIRY, CreationRisk.EXPIRED),
                product("c", CreationRisk.STOCKOUT),
            )
            assertTrue(alerts().check())
            val posted = notifier.posted.single()
            assertEquals(RoleAlertType.NEAR_EXPIRY, posted.type)
            assertEquals("a", posted.targetId)
            assertEquals("Produto a", posted.subject)
            assertEquals("7", posted.userId)
        }
    }

    @Test
    fun `RA-02 manager is alerted of requests awaiting approval, employee is not`() = runTest {
        signIn(UserRole.MANAGER)
        store(
            action("1", ActionStatus.SENT_TO_MANAGER, ActionType.ORDER),
            action("2", ActionStatus.GENERATED),
            action("3", ActionStatus.APPROVED),
            action("4", ActionStatus.SENT_TO_MANAGER, store = 18),
        )
        alerts().check()
        val posted = notifier.posted.single()
        assertEquals(RoleAlertType.PENDING_APPROVAL, posted.type)
        assertEquals("1", posted.targetId)
        assertEquals(ActionType.ORDER, posted.actionType)

        notifier.posted.clear()
        memory.data.clear()
        signIn(UserRole.EMPLOYEE)
        store(action("1", ActionStatus.SENT_TO_MANAGER))
        alerts().check()
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `RA-03 only own approved requests alert the employee`() = runTest {
        signIn(UserRole.EMPLOYEE)
        val useCase = alerts()
        useCase.remember("7", "1")
        useCase.remember("7", "2")
        store(
            action("1", ActionStatus.APPROVED),
            action("2", ActionStatus.REJECTED),
            action("3", ActionStatus.APPROVED),
        )
        useCase.check()
        val posted = notifier.posted.single()
        assertEquals(RoleAlertType.REQUEST_APPROVED, posted.type)
        assertEquals("1", posted.targetId)
        assertTrue(memory.data.getValue("7").ownRequests.isEmpty())
    }

    @Test
    fun `RA-03 employee without own requests does not read actions and manager ignores ownership`() = runTest {
        signIn(UserRole.EMPLOYEE)
        alerts().check()
        coVerify(exactly = 0) { actions.list(any(), any(), any()) }

        signIn(UserRole.MANAGER, id = "9")
        alerts().remember("9", "1")
        store(action("1", ActionStatus.APPROVED))
        alerts().check()
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `RA-04 delivered events are not repeated and near expiry repeats on the next business day`() = runTest {
        signIn(UserRole.MANAGER)
        catalog(product("a", CreationRisk.NEAR_EXPIRY))
        store(action("1", ActionStatus.SENT_TO_MANAGER))
        alerts().check()
        assertEquals(2, notifier.posted.size)
        alerts().check()
        assertEquals(2, notifier.posted.size)

        clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC)
        alerts().check()
        assertEquals(3, notifier.posted.size)
        assertEquals(RoleAlertType.NEAR_EXPIRY, notifier.posted.last().type)
        assertFalse(memory.data.getValue("7").delivered.any { it.startsWith("E:2026-11-02") })
    }

    @Test
    fun `RA-04 a request decided and sent again alerts again`() = runTest {
        signIn(UserRole.MANAGER)
        store(action("1", ActionStatus.SENT_TO_MANAGER))
        alerts().check()
        store(action("1", ActionStatus.APPROVED))
        alerts().check()
        assertTrue(memory.data.getValue("7").delivered.isEmpty())
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `RA-05 disabled type is not posted nor replayed after enabling`() = runTest {
        signIn(UserRole.MANAGER)
        val useCase = alerts()
        assertTrue(useCase.setEnabled(RoleAlertType.PENDING_APPROVAL, false))
        assertFalse(useCase.setEnabled(RoleAlertType.REQUEST_APPROVED, false))
        store(action("1", ActionStatus.SENT_TO_MANAGER))
        catalog(product("a", CreationRisk.NEAR_EXPIRY))
        useCase.check()
        assertEquals(listOf(RoleAlertType.NEAR_EXPIRY), notifier.posted.map { it.type })

        useCase.setEnabled(RoleAlertType.PENDING_APPROVAL, true)
        useCase.check()
        assertEquals(1, notifier.posted.size)
        assertEquals(
            mapOf(RoleAlertType.NEAR_EXPIRY to true, RoleAlertType.PENDING_APPROVAL to true),
            useCase.settings()?.enabled,
        )
    }

    @Test
    fun `RA-12 settings follow the role and are kept per user`() = runTest {
        signIn(UserRole.EMPLOYEE)
        val useCase = alerts()
        useCase.setEnabled(RoleAlertType.NEAR_EXPIRY, false)
        assertEquals(
            mapOf(RoleAlertType.NEAR_EXPIRY to false, RoleAlertType.REQUEST_APPROVED to true),
            useCase.settings()?.enabled,
        )
        signIn(UserRole.EMPLOYEE, id = "8")
        assertEquals(true, useCase.settings()?.enabled?.get(RoleAlertType.NEAR_EXPIRY))
    }

    @Test
    fun `RA-06 nothing is read without permission, active session or ready profile`() = runTest {
        signIn(UserRole.MANAGER)
        notifier.allowed = false
        assertFalse(alerts().check())

        notifier.allowed = true
        sessionState.value = SessionState.Expired
        assertFalse(alerts().check())

        sessionState.value = SessionState.Active
        profileState.value = ProfileState.Loading
        assertFalse(alerts().check())
        assertNull(alerts().settings())

        signIn(UserRole.REGIONAL_MANAGER)
        assertFalse(alerts().check())

        signIn(UserRole.MANAGER)
        RoleAlertType.forRole(UserRole.MANAGER).forEach { alerts().setEnabled(it, false) }
        assertFalse(alerts().check())

        coVerify(exactly = 0) { products.read(any(), any()) }
        coVerify(exactly = 0) { actions.list(any(), any(), any()) }
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `RA-06 a session change during the reads discards the result`() = runTest {
        signIn(UserRole.MANAGER)
        coEvery { actions.list(17, null, expected) } coAnswers {
            every { session.snapshot() } returns expected.copy(generation = 2)
            ActionListResult.Loaded(listOf(action("1", ActionStatus.SENT_TO_MANAGER)))
        }
        assertFalse(alerts().check())
        assertTrue(notifier.posted.isEmpty())
        assertTrue(memory.data.isEmpty())
    }

    @Test
    fun `RA-07 four or more new events of a type become one summary`() = runTest {
        signIn(UserRole.MANAGER)
        store(*(1..4).map { action("$it", ActionStatus.SENT_TO_MANAGER) }.toTypedArray())
        catalog(product("a", CreationRisk.NEAR_EXPIRY), product("b", CreationRisk.NEAR_EXPIRY))
        alerts().check()
        val summary = notifier.posted.single { it.type == RoleAlertType.PENDING_APPROVAL }
        assertNull(summary.targetId)
        assertEquals(4, summary.count)
        assertEquals(2, notifier.posted.count { it.type == RoleAlertType.NEAR_EXPIRY })
    }

    @Test
    fun `RA-09 failed source is skipped and not marked delivered`() = runTest {
        signIn(UserRole.MANAGER)
        coEvery { products.read(17, null) } returns AlertProductsResult.Failure
        coEvery { actions.list(17, null, expected) } returns ActionListResult.Failed
        assertTrue(alerts().check())
        assertTrue(notifier.posted.isEmpty())

        catalog(product("a", CreationRisk.NEAR_EXPIRY))
        store(action("1", ActionStatus.SENT_TO_MANAGER))
        alerts().check()
        assertEquals(2, notifier.posted.size)
    }

    @Test
    fun `RA-10 stop cancels the schedule and removes notifications, start schedules and checks`() = runTest {
        signIn(UserRole.MANAGER)
        val useCase = alerts()
        useCase.start()
        verify { scheduler.start() }
        coVerify { actions.list(17, null, expected) }
        useCase.stop()
        verify { scheduler.stop() }
        assertTrue(notifier.cleared)
    }

    @Test
    fun `RA-10 background run restores the session and stops when it ended`() = runTest {
        signIn(UserRole.MANAGER)
        sessionState.value = SessionState.Restoring
        coEvery { session.restore() } coAnswers {
            sessionState.value = SessionState.Active
            SessionState.Active
        }
        store(action("1", ActionStatus.SENT_TO_MANAGER))
        alerts().runBackground()
        coVerify { profile.load(any()) }
        assertEquals(1, notifier.posted.size)

        sessionState.value = SessionState.Expired
        alerts().runBackground()
        verify { scheduler.stop() }
        assertTrue(notifier.cleared)
    }

    @Test
    fun `RA-08 a tapped alert is only entitled for the same user and role`() {
        val manager = UserProfile("7", "F", "f@example.test", UserRole.MANAGER, UserStatus.ACTIVE, null, 17, "Store")
        val employee = UserProfile("7", "F", "f@example.test", UserRole.EMPLOYEE, UserStatus.ACTIVE, null, 17, "Store")
        val other = UserProfile("8", "F", "f@example.test", UserRole.MANAGER, UserStatus.ACTIVE, null, 17, "Store")
        val pending = AlertTarget(RoleAlertType.PENDING_APPROVAL, "7", "1")
        assertTrue(pending.entitled(manager))
        assertFalse(pending.entitled(employee))
        assertFalse(pending.entitled(other))
        assertTrue(AlertTarget(RoleAlertType.NEAR_EXPIRY, "7", "a").entitled(employee))
        assertFalse(AlertTarget(RoleAlertType.REQUEST_APPROVED, "7", "1").entitled(manager))
    }

    private class MemoryStore : AlertMemoryStore {
        val data = mutableMapOf<String, AlertMemory>()
        override suspend fun read(userId: String) = data[userId] ?: AlertMemory()
        override suspend fun update(userId: String, transform: (AlertMemory) -> AlertMemory) =
            transform(read(userId)).also { data[userId] = it }
    }

    private class RecordingNotifier : AlertNotifier {
        val posted = mutableListOf<RoleAlert>()
        var allowed = true
        var cleared = false
        override fun canNotify() = allowed
        override fun post(alerts: List<RoleAlert>) {
            posted += alerts
        }

        override fun clearAll() {
            cleared = true
        }
    }
}
