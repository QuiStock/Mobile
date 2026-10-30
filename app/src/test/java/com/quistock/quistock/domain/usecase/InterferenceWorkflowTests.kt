package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.StockFlow
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
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InterferenceWorkflowTests {
    private val actions = mockk<InterferenceRepository>()
    private val products = mockk<PriorityAlertsRepository>()
    private val session = mockk<SessionUseCase>()
    private val profile = mockk<ProfileUseCase>()
    private val expected = SessionSnapshot(1, true)
    private val product = AlertProduct(
        "product",
        "Product",
        "Category",
        BigDecimal.ONE,
        null,
        StockFlow.MEDIUM,
        "flow",
        risks = setOf(CreationRisk.STOCKOUT),
    )
    private val draft = ActionDraft(quantity = "20", orderDate = "2026-11-05")
    private val clock = Clock.fixed(Instant.parse("2026-10-30T01:00:00Z"), ZoneOffset.UTC)
    private val create = CreateInterferenceUseCase(actions, products, profile, session, clock)

    private fun setup(role: UserRole = UserRole.EMPLOYEE) {
        every { session.snapshot() } returns expected
        every { session.state } returns MutableStateFlow<SessionState>(SessionState.Active)
        every { profile.state } returns MutableStateFlow<ProfileState>(
            ProfileState.Ready(
                1,
                UserProfile("fixture", "Fixture", "fixture@example.test", role, UserStatus.ACTIVE, null, 17, "Store"),
            ),
        )
        coEvery { products.read(17, null) } returns AlertProductsResult.Success(listOf(product), 0)
        coEvery { actions.list(17, ActionType.ORDER, expected) } returns ActionListResult.Loaded(emptyList())
        coEvery { actions.generate(any(), any(), any(), any(), any()) } returns ActionResult.Empty
    }

    @Test
    fun `pending other analysis and same analysis final actions are returned without generation`() = runTest {
        setup()
        ActionStatus.entries.forEach { status ->
            val flow = if (status.pending) "other" else "flow"
            val existing = InterferenceAction("existing", flow, "product", 17, ActionType.ORDER, status, draft)
            coEvery { actions.list(17, ActionType.ORDER, expected) } returns ActionListResult.Loaded(listOf(existing))
            val result = create.generate(product, ActionType.ORDER, draft, expected) as ActionResult.Found
            assertTrue(result.existing)
            assertEquals(existing, result.action)
        }
        coVerify(exactly = 0) { actions.generate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `final other analysis releases generation while changed current flow blocks it`() = runTest {
        setup()
        listOf(ActionStatus.APPROVED, ActionStatus.REJECTED).forEach { status ->
            val existing = InterferenceAction("existing", "other", "product", 17, ActionType.ORDER, status, draft)
            coEvery { actions.list(17, ActionType.ORDER, expected) } returns ActionListResult.Loaded(listOf(existing))
            assertEquals(ActionResult.Empty, create.generate(product, ActionType.ORDER, draft, expected))
        }
        coVerify(exactly = 2) { actions.generate(product, 17, ActionType.ORDER, draft, expected) }
        coEvery { products.read(17, null) } returns AlertProductsResult.Success(
            listOf(product.copy(analysisId = "new")),
            0,
        )
        val result = create.generate(product, ActionType.ORDER, draft, expected) as ActionResult.Failed
        assertEquals(ActionFailure.CONFLICT, result.reason)
        coVerify(exactly = 2) { actions.generate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `manager stale generation and wrong store action cannot write`() = runTest {
        setup(UserRole.MANAGER)
        assertEquals(
            ActionFailure.FORBIDDEN,
            (create.generate(product, ActionType.ORDER, draft, expected) as ActionResult.Failed).reason,
        )
        setup()
        val wrongStore = InterferenceAction(
            "action",
            "flow",
            "product",
            18,
            ActionType.ORDER,
            ActionStatus.GENERATED,
            draft,
        )
        assertEquals(ActionFailure.FORBIDDEN, (create.forward(wrongStore, expected) as ActionResult.Failed).reason)
        every { session.snapshot() } returns expected.copy(generation = 2)
        assertEquals(
            ActionFailure.FORBIDDEN,
            (create.generate(product, ActionType.ORDER, draft, expected) as ActionResult.Failed).reason,
        )
        coVerify(exactly = 0) { actions.generate(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { actions.forward(any(), any()) }
    }

    @Test
    fun `risk on insufficient analysis cannot generate either action type`() = runTest {
        setup()
        val insufficient = product.copy(flow = null, risks = setOf(CreationRisk.STOCKOUT, CreationRisk.EXPIRATION))
        ActionType.entries.forEach { type ->
            val result = create.generate(insufficient, type, draft, expected) as ActionResult.Failed
            assertEquals(ActionFailure.INVALID, result.reason)
        }
        coVerify(exactly = 0) { actions.list(any(), any(), any()) }
        coVerify(exactly = 0) { actions.generate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `today uses Sao Paulo even when injected clock has UTC zone`() {
        assertEquals("2026-10-29", create.today().toString())
    }
}
