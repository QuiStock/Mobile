package com.quistock.quistock.presentation.interferences

import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.usecase.CreateInterferenceUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CreationSubmissionTests {
    private val create = mockk<CreateInterferenceUseCase>()
    private val session = mockk<SessionUseCase>()
    private val expected = SessionSnapshot(1, true)
    private val draft = ActionDraft(quantity = "20", orderDate = "2026-11-05")
    private val product = AlertProduct("product", "Product", "Category", BigDecimal.ONE, null, StockFlow.LOW, "flow")
    private val action = InterferenceAction(
        "action",
        "flow",
        "product",
        17,
        ActionType.ORDER,
        ActionStatus.GENERATED,
        draft,
    )

    private fun state() = MutableStateFlow(
        CreationState(phase = CreationPhase.REVIEW, product = product, draft = draft),
    )

    private fun setup() {
        every { session.snapshot() } returns expected
        every { create.today() } returns LocalDate.of(2026, 10, 29)
    }

    @Test
    fun `known forwarding failure retains action and manual retry sends only patch`() = runTest {
        setup()
        coEvery { create.generate(product, ActionType.ORDER, draft, expected) } returns ActionResult.Found(action)
        coEvery { create.forward(action, expected) } returns ActionResult.Failed(ActionFailure.REJECTED)
        val state = state()
        val submission = CreationSubmission(create, session, state, this) { true }
        submission.confirm()
        submission.confirm()
        runCurrent()
        assertEquals(CreationPhase.EXISTING, state.value.phase)
        assertEquals(action, state.value.action)
        submission.forward()
        runCurrent()
        coVerify(exactly = 1) { create.generate(any(), any(), any(), any()) }
        coVerify(exactly = 2) { create.forward(action, expected) }
    }

    @Test
    fun `uncertain write blocks resubmission and empty result never forwards`() = runTest {
        setup()
        coEvery { create.generate(any(), any(), any(), any()) } returns ActionResult.Failed(ActionFailure.UNCERTAIN)
        val state = state()
        val submission = CreationSubmission(create, session, state, this) { true }
        submission.confirm()
        runCurrent()
        assertTrue(state.value.mustCheckList)
        submission.confirm()
        coVerify(exactly = 1) { create.generate(any(), any(), any(), any()) }
        state.value = state.value.copy(phase = CreationPhase.REVIEW)
        coEvery { create.generate(any(), any(), any(), any()) } returns ActionResult.Empty
        submission.confirm()
        runCurrent()
        assertEquals(CreationPhase.EMPTY, state.value.phase)
        coVerify(exactly = 0) { create.forward(any(), any()) }
    }

    @Test
    fun `late result from a revoked session is not published or forwarded`() = runTest {
        setup()
        val pending = CompletableDeferred<ActionResult>()
        coEvery { create.generate(any(), any(), any(), any()) } coAnswers { pending.await() }
        val state = state()
        var current = true
        val submission = CreationSubmission(create, session, state, this) { current }
        submission.confirm()
        runCurrent()
        current = false
        pending.complete(ActionResult.Found(action))
        runCurrent()
        assertEquals(CreationPhase.CHECKING, state.value.phase)
        coVerify(exactly = 0) { create.forward(any(), any()) }
    }

    @Test
    fun `existing persisted values require a second review and dates revalidate before writing`() = runTest {
        setup()
        coEvery { create.generate(any(), any(), any(), any()) } returns ActionResult.Found(action, existing = true)
        val state = state()
        val submission = CreationSubmission(create, session, state, this) { true }
        submission.confirm()
        runCurrent()
        assertEquals(CreationPhase.EXISTING, state.value.phase)
        coVerify(exactly = 0) { create.forward(any(), any()) }
        state.value = state.value.copy(phase = CreationPhase.REVIEW, draft = draft)
        every { create.today() } returns LocalDate.of(2026, 11, 6)
        submission.confirm()
        assertEquals(CreationPhase.FORM, state.value.phase)
        assertTrue(state.value.errors.isNotEmpty())
        coVerify(exactly = 1) { create.generate(any(), any(), any(), any()) }
    }

    @Test
    fun `reused action keeps requested values distinct from persisted values and is not forwarded automatically`() =
        runTest {
            setup()
            val requested = ActionDraft(quantity = "67", orderDate = "2026-11-12")
            val persisted = action.copy(values = draft, status = ActionStatus.SENT_TO_MANAGER)
            val state = MutableStateFlow(
                CreationState(phase = CreationPhase.REVIEW, product = product, draft = requested),
            )
            coEvery { create.generate(product, ActionType.ORDER, requested, expected) } returns
                ActionResult.Found(persisted, existing = true)
            val submission = CreationSubmission(create, session, state, this) { true }

            submission.confirm()
            runCurrent()

            assertEquals(CreationPhase.EXISTING, state.value.phase)
            assertEquals(persisted, state.value.action)
            assertEquals(requested, state.value.draft)
            assertEquals(requested, state.value.requestedDraft)
            assertTrue(state.value.requestDiffersFromExisting)
            coVerify(exactly = 0) { create.forward(any(), any()) }
        }

    @Test
    fun `refresh errors take precedence over reused values while retaining request notice`() {
        val reused = CreationState(
            phase = CreationPhase.EXISTING,
            action = action.copy(status = ActionStatus.SENT_TO_MANAGER),
            requestedDraft = ActionDraft(quantity = "67", orderDate = "2026-11-12"),
            failure = ActionFailure.READ,
        )

        assertEquals(R.string.creation_action_refresh_error, CreationPresentation.existingNotice(reused))
        assertEquals(
            R.string.creation_action_missing,
            CreationPresentation.existingNotice(reused.copy(failure = ActionFailure.NOT_FOUND)),
        )
        assertEquals(
            R.string.creation_action_refreshing,
            CreationPresentation.existingNotice(reused.copy(failure = null, loading = true)),
        )
    }

    @Test
    fun `generation returning an already sent action is not reported as a new success`() = runTest {
        setup()
        val requested = ActionDraft(quantity = "67", orderDate = "2026-11-12")
        val concurrent = action.copy(
            values = draft,
            status = ActionStatus.SENT_TO_MANAGER,
        )
        val state = MutableStateFlow(
            CreationState(phase = CreationPhase.REVIEW, product = product, draft = requested),
        )
        coEvery { create.generate(product, ActionType.ORDER, requested, expected) } returns
            ActionResult.Found(concurrent, existing = false)
        val submission = CreationSubmission(create, session, state, this) { true }

        submission.confirm()
        runCurrent()

        assertEquals(CreationPhase.EXISTING, state.value.phase)
        assertEquals(concurrent, state.value.action)
        assertEquals(requested, state.value.requestedDraft)
        assertTrue(state.value.requestDiffersFromExisting)
        coVerify(exactly = 0) { create.forward(any(), any()) }
    }

    @Test
    fun `unauthorized existing action cannot be forwarded`() = runTest {
        setup()
        val state = MutableStateFlow(
            CreationState(
                phase = CreationPhase.EXISTING,
                action = action,
                failure = ActionFailure.UNAUTHORIZED,
            ),
        )
        val submission = CreationSubmission(create, session, state, this) { false }

        submission.forward()

        assertEquals(CreationPhase.EXISTING, state.value.phase)
        assertEquals(ActionFailure.UNAUTHORIZED, state.value.failure)
        coVerify(exactly = 0) { create.forward(any(), any()) }
    }
}
