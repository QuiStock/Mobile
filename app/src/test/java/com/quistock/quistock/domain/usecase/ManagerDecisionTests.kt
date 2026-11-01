package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionDecision
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.Justification
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.InterferenceRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerDecisionTests {
    private val actions = mockk<InterferenceRepository>()
    private val products = mockk<PriorityAlertsRepository>()
    private val session = mockk<SessionUseCase>()
    private val profile = mockk<ProfileUseCase>()
    private val expected = SessionSnapshot(1, true)
    private val decisions = ManagerDecisionUseCase(actions, products, profile, session)
    private val draft = ActionDraft(quantity = "20", orderDate = "2026-11-05")

    private fun action(status: ActionStatus = ActionStatus.SENT_TO_MANAGER, store: Long = 17, id: String = "action") =
        InterferenceAction(id, "flow", "product", store, ActionType.ORDER, status, draft)

    private fun setup(role: UserRole = UserRole.MANAGER) {
        every { session.snapshot() } returns expected
        every { session.state } returns MutableStateFlow<SessionState>(SessionState.Active)
        every { profile.state } returns MutableStateFlow<ProfileState>(
            ProfileState.Ready(
                1,
                UserProfile("fixture", "Fixture", "fixture@example.test", role, UserStatus.ACTIVE, null, 17, "Store"),
            ),
        )
        coEvery { actions.decide(any(), any(), any(), any()) } answers {
            ActionResult.Found(firstArg<InterferenceAction>().copy(status = secondArg<ActionDecision>().status))
        }
    }

    @Test
    fun `only a manager of the action store can decide`() = runTest {
        setup(UserRole.EMPLOYEE)
        val employee = decisions.decide(action(), ActionDecision.APPROVE, "", expected) as ActionResult.Failed
        assertEquals(ActionFailure.FORBIDDEN, employee.reason)
        setup()
        val otherStore = decisions.decide(action(store = 99), ActionDecision.APPROVE, "", expected)
        assertEquals(ActionFailure.FORBIDDEN, (otherStore as ActionResult.Failed).reason)
        coVerify(exactly = 0) { actions.decide(any(), any(), any(), any()) }
    }

    @Test
    fun `only requests sent to the manager accept a decision`() = runTest {
        setup()
        ActionStatus.entries.filterNot { it == ActionStatus.SENT_TO_MANAGER }.forEach { status ->
            val result = decisions.decide(action(status), ActionDecision.APPROVE, "", expected) as ActionResult.Failed
            assertEquals(ActionFailure.CONFLICT, result.reason)
        }
        coVerify(exactly = 0) { actions.decide(any(), any(), any(), any()) }
    }

    @Test
    fun `rejection requires a nonblank trimmed justification and approval sends none when empty`() = runTest {
        setup()
        val blank = decisions.decide(action(), ActionDecision.REJECT, "   ", expected) as ActionResult.Failed
        assertEquals(ActionFailure.INVALID, blank.reason)
        coVerify(exactly = 0) { actions.decide(any(), any(), any(), any()) }

        val rejected = decisions.decide(action(), ActionDecision.REJECT, "  Sem verba  ", expected)
        assertEquals(ActionStatus.REJECTED, (rejected as ActionResult.Found).action.status)
        coVerify { actions.decide(action(), ActionDecision.REJECT, "Sem verba", expected) }

        decisions.decide(action(), ActionDecision.APPROVE, "", expected)
        coVerify { actions.decide(action(), ActionDecision.APPROVE, null, expected) }
    }

    @Test
    fun `justification rule matches the Core contract`() {
        assertEquals(Justification.Missing, ActionDecision.REJECT.justification(" "))
        assertEquals(Justification.Valid(null), ActionDecision.APPROVE.justification(" "))
        assertEquals(Justification.Valid("ok"), ActionDecision.APPROVE.justification(" ok "))
    }

    @Test
    fun `list keeps forwarded and decided requests of the manager store only`() = runTest {
        setup()
        val all = ActionStatus.entries.map { action(it, id = it.name) }
        coEvery { actions.list(17, null, expected) } returns ActionListResult.Loaded(all)
        val listed = decisions.list(expected) as ActionListResult.Loaded
        assertEquals(
            setOf(ActionStatus.SENT_TO_MANAGER, ActionStatus.APPROVED, ActionStatus.REJECTED),
            listed.actions.map { it.status }.toSet(),
        )
        setup(UserRole.EMPLOYEE)
        assertTrue(decisions.list(expected) is ActionListResult.Failed)
    }
}
