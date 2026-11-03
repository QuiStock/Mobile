package com.quistock.quistock.presentation.interferences

import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.ActionDecision
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.usecase.ManagerDecisionUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerInterferencesViewModelTests {
    @get:Rule val main = MainDispatcherRule()

    private val decisions = mockk<ManagerDecisionUseCase>()
    private val session = mockk<SessionUseCase>()
    private val profile = mockk<ProfileUseCase>()
    private val expected = SessionSnapshot(1, true)
    private val ready = ProfileState.Ready(
        1,
        UserProfile(
            "fixture",
            "Fixture",
            "fixture@example.test",
            UserRole.MANAGER,
            UserStatus.ACTIVE,
            null,
            17,
            "Store",
        ),
    )
    private val profileState = MutableStateFlow<ProfileState>(ready)
    private val draft = ActionDraft(quantity = "20", orderDate = "2026-11-05")
    private val pending = InterferenceAction(
        "pending",
        "flow",
        "product",
        17,
        ActionType.ORDER,
        ActionStatus.SENT_TO_MANAGER,
        draft,
    )
    private val approved = pending.copy(id = "approved", type = ActionType.PROMOTION, status = ActionStatus.APPROVED)

    private fun model(): ManagerInterferencesViewModel {
        every { session.snapshot() } returns expected
        every { session.state } returns MutableStateFlow<SessionState>(SessionState.Active)
        every { profile.state } returns profileState
        every { decisions.context(expected) } returns ready
        coEvery { decisions.list(expected) } returns ActionListResult.Loaded(listOf(pending, approved))
        coEvery { decisions.catalog(expected) } returns AlertProductsResult.Failure
        return ManagerInterferencesViewModel(decisions, session, profile)
    }

    @Test
    fun `list separates pending and decided requests and filters by type`() = runTest {
        val model = model()
        model.refresh()
        runCurrent()
        assertEquals(listOf(pending), model.state.value.pending)
        assertEquals(listOf(approved), model.state.value.decided)
        model.toggleFilter(ActionType.PROMOTION)
        assertTrue(model.state.value.pending.isEmpty())
        model.toggleFilter(ActionType.PROMOTION)
        assertEquals(listOf(pending), model.state.value.pending)
    }

    @Test
    fun `decision updates the request once and repeated taps are ignored while in flight`() = runTest {
        val model = model()
        val gate = CompletableDeferred<ActionResult>()
        coEvery { decisions.decide(pending, ActionDecision.REJECT, "Sem verba", expected) } coAnswers { gate.await() }
        model.refresh()
        runCurrent()
        model.select(pending)
        model.decide(ActionDecision.REJECT, "Sem verba")
        model.decide(ActionDecision.REJECT, "Sem verba")
        model.decide(ActionDecision.APPROVE)
        runCurrent()
        assertTrue(model.state.value.submitting)
        gate.complete(ActionResult.Found(pending.copy(status = ActionStatus.REJECTED)))
        runCurrent()
        coVerify(exactly = 1) { decisions.decide(any(), any(), any(), any()) }
        assertEquals(DecisionPhase.DONE, model.state.value.phase)
        assertEquals(ActionStatus.REJECTED, model.state.value.selected?.status)
        assertTrue(model.state.value.pending.isEmpty())
    }

    @Test
    fun `decided requests are locked against new decisions`() = runTest {
        val model = model()
        model.refresh()
        runCurrent()
        model.select(approved)
        model.decide(ActionDecision.REJECT, "x")
        runCurrent()
        coVerify(exactly = 0) { decisions.decide(any(), any(), any(), any()) }
        assertFalse(model.state.value.submitting)
    }

    @Test
    fun `conflict reports failure and reloads Core state`() = runTest {
        val model = model()
        coEvery { decisions.decide(any(), any(), any(), any()) } returns ActionResult.Failed(ActionFailure.CONFLICT)
        model.refresh()
        runCurrent()
        model.select(pending)
        model.decide(ActionDecision.APPROVE)
        runCurrent()
        assertEquals(ActionFailure.CONFLICT, model.state.value.failure)
        coVerify(exactly = 2) { decisions.list(expected) }
        model.acknowledge()
        assertEquals(DecisionPhase.IDLE, model.state.value.phase)
    }

    @Test
    fun `new session generation discards the previous store requests`() = runTest {
        val model = model()
        model.refresh()
        runCurrent()
        profileState.value = ProfileState.Ready(2, ready.profile)
        runCurrent()
        assertTrue(model.state.value.actions.isEmpty())
    }

    @Test
    fun `alert focus resolves to the request only after a fresh read and only while pending`() = runTest {
        val model = model()
        model.focus("pending")
        assertEquals(null, model.takeFocus())
        runCurrent()
        assertEquals(pending, model.takeFocus())
        assertEquals(null, model.state.value.focusId)

        model.focus("approved")
        runCurrent()
        assertEquals(null, model.takeFocus())
        model.focus("unknown")
        runCurrent()
        assertEquals(null, model.takeFocus())
    }
}
