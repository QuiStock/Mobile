package com.quistock.quistock.presentation.interferences

import androidx.lifecycle.ViewModelStore
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.ProfileState
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
import io.mockk.every
import io.mockk.mockk
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
}
