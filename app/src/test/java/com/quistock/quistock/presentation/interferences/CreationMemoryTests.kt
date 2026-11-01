package com.quistock.quistock.presentation.interferences

import androidx.lifecycle.ViewModelStore
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.usecase.CreateInterferenceUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class CreationMemoryTests {
    private val store = ViewModelStore()
    private val create = mockk<CreateInterferenceUseCase>()
    private val session = mockk<SessionUseCase>()
    private val profile = mockk<ProfileUseCase>()
    private val status = MutableStateFlow<SessionState>(SessionState.Active)
    private val ready = ProfileState.Ready(
        1,
        UserProfile(
            "fixture",
            "Fixture",
            "fixture@example.test",
            UserRole.EMPLOYEE,
            UserStatus.ACTIVE,
            null,
            17,
            "Store",
        ),
    )
    private val profileState = MutableStateFlow<ProfileState>(ready)
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
    private val action = InterferenceAction(
        "action",
        "flow",
        product.id,
        17,
        ActionType.ORDER,
        ActionStatus.GENERATED,
        ActionDraft(quantity = "20", orderDate = "2026-11-05"),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { session.snapshot() } returns expected
        every { session.state } returns status
        every { profile.state } returns profileState
        every { create.context(expected) } answers { if (status.value == SessionState.Active) ready else null }
        coEvery { create.catalog(expected) } returns AlertProductsResult.Success(listOf(product), 0)
        coEvery { create.list(expected, any()) } returns ActionListResult.Loaded(emptyList())
    }

    @After
    fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `draft survives reentry and list refresh while logout clears it`() = runTest {
        val model = CreateInterferenceViewModel(create, session, profile)
        store.put("creation", model)
        runCurrent()
        model.enter(ActionType.ORDER, product.id)
        runCurrent()
        val draft = ActionDraft(quantity = "20", orderDate = "2026-11-05")
        model.edit(draft)
        model.enter(ActionType.ORDER)
        model.refreshList()
        runCurrent()
        assertEquals(draft, model.state.value.draft)
        assertEquals(product, model.state.value.product)
        status.value = SessionState.LoggingOut
        runCurrent()
        assertFalse(model.hasDraft)
        assertEquals(null, model.state.value.action)
    }

    @Test
    fun `new activity model after process loss starts without draft`() = runTest {
        val model = CreateInterferenceViewModel(create, session, profile)
        store.put("creation", model)
        runCurrent()
        assertFalse(model.hasDraft)
        assertEquals(CreationPhase.SELECT, model.state.value.phase)
    }

    @Test
    fun `refreshing existing action updates its stored status without resubmitting`() = runTest {
        val forwarded = action.copy(status = ActionStatus.SENT_TO_MANAGER)
        coEvery { create.list(expected, null) } returnsMany listOf(
            ActionListResult.Loaded(listOf(action)),
            ActionListResult.Loaded(listOf(forwarded)),
        )
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()
        model.load(ActionType.ORDER, product.id)
        runCurrent()
        model.showExisting(action)

        model.refreshExisting()
        runCurrent()

        assertEquals(CreationPhase.EXISTING, model.state.value.phase)
        assertEquals(forwarded, model.state.value.action)
        assertEquals(null, model.state.value.failure)
        coVerify(exactly = 0) { create.generate(any(), any(), any(), any()) }
        coVerify(exactly = 0) { create.forward(any(), any()) }
    }

    @Test
    fun `missing action in refreshed list keeps last persisted snapshot visible`() = runTest {
        coEvery { create.list(expected, null) } returnsMany listOf(
            ActionListResult.Loaded(listOf(action)),
            ActionListResult.Loaded(emptyList()),
        )
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()
        model.load(ActionType.ORDER, product.id)
        runCurrent()
        model.showExisting(action)

        model.refreshExisting()
        runCurrent()

        assertEquals(CreationPhase.EXISTING, model.state.value.phase)
        assertEquals(action, model.state.value.action)
        assertEquals(ActionFailure.NOT_FOUND, model.state.value.failure)
        model.forward()
        coVerify(exactly = 0) { create.generate(any(), any(), any(), any()) }
        coVerify(exactly = 0) { create.forward(any(), any()) }
    }

    @Test
    fun `missing creation context reports failure and blocks forwarding`() = runTest {
        coEvery { create.list(expected, null) } returns ActionListResult.Loaded(listOf(action))
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()
        model.load(ActionType.ORDER, product.id)
        runCurrent()
        model.showExisting(action)
        every { create.context(expected) } returns null

        model.refreshExisting()
        model.forward()

        assertEquals(CreationPhase.EXISTING, model.state.value.phase)
        assertEquals(action, model.state.value.action)
        assertEquals(ActionFailure.FORBIDDEN, model.state.value.failure)
        coVerify(exactly = 0) { create.forward(any(), any()) }
    }

    @Test
    fun `session failure during refresh clears loading without publishing stale status`() = runTest {
        val staleRefresh = action.copy(status = ActionStatus.SENT_TO_MANAGER)
        val pending = CompletableDeferred<ActionListResult>()
        var requests = 0
        coEvery { create.list(expected, null) } coAnswers {
            if (requests++ == 0) ActionListResult.Loaded(listOf(action)) else pending.await()
        }
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()
        model.load(ActionType.ORDER, product.id)
        runCurrent()
        model.showExisting(action)

        model.refreshExisting()
        runCurrent()
        assertTrue(model.state.value.loading)

        status.value = SessionState.Failure(SessionFailure.NETWORK)
        runCurrent()
        pending.complete(ActionListResult.Loaded(listOf(staleRefresh)))
        runCurrent()

        assertFalse(model.state.value.loading)
        assertEquals(action, model.state.value.action)
        assertEquals(ActionFailure.UNAUTHORIZED, model.state.value.failure)
        coVerify(exactly = 0) { create.forward(any(), any()) }
    }

    @Test
    fun `session failure during product and action load clears spinner without publishing stale data`() = runTest {
        val pendingCatalog = CompletableDeferred<AlertProductsResult>()
        coEvery { create.catalog(expected) } coAnswers { pendingCatalog.await() }
        coEvery { create.list(expected, null) } returns ActionListResult.Loaded(listOf(action))
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()

        model.load(ActionType.ORDER, product.id)
        runCurrent()
        assertTrue(model.state.value.loading)

        status.value = SessionState.Failure(SessionFailure.NETWORK)
        runCurrent()
        pendingCatalog.complete(AlertProductsResult.Success(listOf(product), 0))
        runCurrent()

        assertFalse(model.state.value.loading)
        assertTrue(model.state.value.listFailed)
        assertEquals(ActionFailure.UNAUTHORIZED, model.state.value.failure)
        assertTrue(model.state.value.products.isEmpty())
        assertTrue(model.state.value.actions.isEmpty())
        assertEquals(CreationPhase.SELECT, model.state.value.phase)
    }

    @Test
    fun `session failure during hub list refresh clears spinner and marks list unavailable`() = runTest {
        val pendingActions = CompletableDeferred<ActionListResult>()
        var requests = 0
        coEvery { create.list(expected, null) } coAnswers {
            if (requests++ == 0) ActionListResult.Loaded(listOf(action)) else pendingActions.await()
        }
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()
        model.load(ActionType.ORDER)
        runCurrent()

        model.refreshList()
        runCurrent()
        assertTrue(model.state.value.loading)

        status.value = SessionState.Failure(SessionFailure.NETWORK)
        runCurrent()
        pendingActions.complete(ActionListResult.Loaded(emptyList()))
        runCurrent()

        assertFalse(model.state.value.loading)
        assertTrue(model.state.value.listFailed)
        assertEquals(ActionFailure.UNAUTHORIZED, model.state.value.failure)
        assertEquals(listOf(action), model.state.value.actions)
    }

    @Test
    fun `opening another action is blocked while existing snapshot refresh is running`() = runTest {
        val other = action.copy(id = "other", values = ActionDraft(quantity = "5", orderDate = "2026-11-06"))
        val refreshed = action.copy(status = ActionStatus.SENT_TO_MANAGER)
        val pending = CompletableDeferred<ActionListResult>()
        var requests = 0
        coEvery { create.list(expected, null) } coAnswers {
            if (requests++ == 0) ActionListResult.Loaded(listOf(action, other)) else pending.await()
        }
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()
        model.load(ActionType.ORDER, product.id)
        runCurrent()
        model.showExisting(action)

        model.refreshExisting()
        runCurrent()
        model.showExisting(other)
        assertEquals(action, model.state.value.action)

        pending.complete(ActionListResult.Loaded(listOf(refreshed, other)))
        runCurrent()

        assertEquals(refreshed, model.state.value.action)
        assertEquals(CreationPhase.EXISTING, model.state.value.phase)
    }

    @Test
    fun `selecting a product with a pending action of the same type opens the existing action`() = runTest {
        coEvery { create.list(expected, null) } returns ActionListResult.Loaded(listOf(action))
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()

        model.load(ActionType.ORDER)
        runCurrent()
        model.select(product)

        assertEquals(CreationPhase.EXISTING, model.state.value.phase)
        assertEquals(action, model.state.value.action)
        assertEquals(R.string.creation_pending_exists, CreationPresentation.existingNotice(model.state.value))

        model.discard()
        model.load(ActionType.ORDER, product.id)
        runCurrent()
        assertEquals(CreationPhase.EXISTING, model.state.value.phase)
        assertEquals(action, model.state.value.action)
    }

    @Test
    fun `selection allows a different type or a final action from another analysis`() = runTest {
        val productWithBothTypes = product.copy(risks = setOf(CreationRisk.STOCKOUT, CreationRisk.NEAR_EXPIRY))
        val otherType = action.copy(type = ActionType.PROMOTION, status = ActionStatus.GENERATED)
        val otherProduct = action.copy(productId = "other-product")
        val otherStore = action.copy(storeId = 18)
        coEvery { create.list(expected, null) } returns ActionListResult.Loaded(
            listOf(otherType, otherProduct, otherStore),
        )
        coEvery { create.catalog(expected) } returns AlertProductsResult.Success(listOf(productWithBothTypes), 0)
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()

        model.load(ActionType.ORDER, product.id)
        runCurrent()
        model.select(productWithBothTypes)
        assertEquals(CreationPhase.FORM, model.state.value.phase)

        model.discard()
        val finalAction = action.copy(status = ActionStatus.APPROVED, flowId = "older-analysis")
        coEvery { create.list(expected, null) } returns ActionListResult.Loaded(listOf(finalAction))
        model.load(ActionType.ORDER, product.id)
        runCurrent()
        model.select(productWithBothTypes)
        assertEquals(CreationPhase.FORM, model.state.value.phase)
    }

    @Test
    fun `final action from the same analysis opens its summary instead of another form`() = runTest {
        val finalAction = action.copy(status = ActionStatus.APPROVED)
        coEvery { create.list(expected, null) } returns ActionListResult.Loaded(listOf(finalAction))
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()

        model.load(ActionType.ORDER, product.id)
        runCurrent()

        assertEquals(CreationPhase.EXISTING, model.state.value.phase)
        assertEquals(finalAction, model.state.value.action)
        assertEquals(R.string.creation_final_exists, CreationPresentation.existingNotice(model.state.value))
    }

    @Test
    fun `failed action list blocks selection until a successful refresh`() = runTest {
        coEvery { create.list(expected, null) } returnsMany listOf(
            ActionListResult.Failed,
            ActionListResult.Loaded(emptyList()),
        )
        val model = CreateInterferenceViewModel(create, session, profile)
        runCurrent()

        model.load(ActionType.ORDER, product.id)
        runCurrent()
        assertEquals(CreationPhase.SELECT, model.state.value.phase)
        assertEquals(null, model.state.value.product)
        assertTrue(model.state.value.listFailed)
        model.select(product)

        assertEquals(CreationPhase.SELECT, model.state.value.phase)
        assertEquals(ActionFailure.READ, model.state.value.failure)
        assertTrue(model.state.value.listFailed)
        model.review()
        model.confirm()
        runCurrent()
        coVerify(exactly = 0) { create.generate(any(), any(), any(), any()) }

        model.load(ActionType.ORDER, product.id)
        runCurrent()
        model.select(product)
        assertEquals(CreationPhase.FORM, model.state.value.phase)
    }
}
