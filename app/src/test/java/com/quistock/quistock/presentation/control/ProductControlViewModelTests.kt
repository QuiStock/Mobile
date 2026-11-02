package com.quistock.quistock.presentation.control

import androidx.lifecycle.ViewModelStore
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.port.ProfileRepository
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ProductControlViewModelTests {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val session = SessionUseCase(auth)
    private val repository = mockk<PriorityAlertsRepository>()
    private val profileRepository = mockk<ProfileRepository>()
    private val profile = ProfileUseCase(profileRepository, session)
    private var storeId = 17L
    private var storeCode = "store-a"
    private var binding = SessionBinding("session-a", "user-a", storeId, Long.MAX_VALUE)

    init {
        every { auth.hasAccess(any()) } returns true
        every { auth.currentBinding(any()) } answers { binding }
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        coEvery { auth.bindProfile(any(), any(), any()) } coAnswers {
            binding = binding.copy(userId = secondArg(), storeId = thirdArg())
            binding
        }
        coEvery { profileRepository.read() } coAnswers {
            ProfileResult.Success(user(storeId, storeCode))
        }
        coEvery { repository.read(any(), any()) } returns AlertProductsResult.Success(emptyList(), 0)
    }

    @After
    fun cleanup() {
        viewModels.clear()
    }

    @Test
    fun `loads all returned products including those without analysis for the active store`() = runTest {
        val analyzed = product("analyzed", "Leite", StockFlow.MEDIUM)
        val unanalyzed = product("unanalyzed", "Arroz", null)
        coEvery { repository.read(17, "store-a") } returns AlertProductsResult.Success(
            listOf(analyzed, unanalyzed),
            invalidCount = 1,
        )

        val model = model()
        runCurrent()

        assertEquals(ProductControlState.Ready(listOf(analyzed, unanalyzed), partial = true), model.state.value)
        assertEquals(listOf(analyzed, unanalyzed), model.visibleProducts())
        coVerify(exactly = 1) { repository.read(17, "store-a") }
    }

    @Test
    fun `search and flow filters refine loaded products and can be reset`() = runTest {
        val high = product("high", "Leite integral", StockFlow.HIGH)
        val medium = product("medium", "Leite em pó", StockFlow.MEDIUM)
        val low = product("low", "Arroz", StockFlow.LOW)
        val unavailable = product("none", "Feijão", null)
        coEvery { repository.read(any(), any()) } returns
            AlertProductsResult.Success(listOf(high, medium, low, unavailable), invalidCount = 0)
        val model = model()
        runCurrent()

        model.setFilter(ProductFlowFilter.HIGH)
        assertEquals(listOf(high), model.visibleProducts())
        model.setFilter(ProductFlowFilter.MEDIUM)
        assertEquals(listOf(medium), model.visibleProducts())
        model.setFilter(ProductFlowFilter.LOW)
        assertEquals(listOf(low), model.visibleProducts())
        model.setFilter(ProductFlowFilter.UNAVAILABLE)
        assertEquals(listOf(unavailable), model.visibleProducts())
        model.setFilter(ProductFlowFilter.ALL)
        model.setSearch(" leite ")
        assertEquals(listOf(high, medium), model.visibleProducts())
        model.setSearch("INTEGRAL")
        assertEquals(listOf(high), model.visibleProducts())
        model.setSearch("")
        assertEquals(listOf(high, medium, low, unavailable), model.visibleProducts())
    }

    @Test
    fun `failure stays visible until retry reloads the active store`() = runTest {
        val recovered = product("recovered", "Produto", StockFlow.LOW)
        coEvery { repository.read(17, "store-a") } returnsMany listOf(
            AlertProductsResult.Failure,
            AlertProductsResult.Success(listOf(recovered), invalidCount = 0),
        )
        val model = model()
        runCurrent()

        assertEquals(ProductControlState.Error, model.state.value)
        model.enter()
        runCurrent()
        assertEquals(ProductControlState.Error, model.state.value)
        coVerify(exactly = 1) { repository.read(17, "store-a") }

        model.retry()
        runCurrent()

        assertEquals(ProductControlState.Ready(listOf(recovered), partial = false), model.state.value)
        coVerify(exactly = 2) { repository.read(17, "store-a") }
    }

    @Test
    fun `late response from previous store cannot replace the new store products`() = runTest {
        val oldResponse = CompletableDeferred<AlertProductsResult>()
        val oldProduct = product("old", "Produto antigo", StockFlow.HIGH)
        val newProduct = product("new", "Produto atual", StockFlow.LOW)
        coEvery { repository.read(17, "store-a") } coAnswers {
            withContext(NonCancellable) { oldResponse.await() }
        }
        coEvery { repository.read(52, "store-b") } returns
            AlertProductsResult.Success(listOf(newProduct), invalidCount = 0)

        val model = model()
        runCurrent()
        coVerify(exactly = 1) { repository.read(17, "store-a") }

        session.expire(session.snapshot())
        runCurrent()
        storeId = 52
        storeCode = "store-b"
        binding = binding.copy(sessionId = "session-b", userId = "user-b", storeId = storeId)
        session.login("other@example.test", "fixture")
        profile.load()
        runCurrent()
        assertEquals(ProductControlState.Ready(listOf(newProduct), partial = false), model.state.value)
        assertEquals(listOf(newProduct), model.visibleProducts())

        oldResponse.complete(AlertProductsResult.Success(listOf(oldProduct), invalidCount = 0))
        runCurrent()

        assertEquals(ProductControlState.Ready(listOf(newProduct), partial = false), model.state.value)
        assertEquals(listOf(newProduct), model.visibleProducts())
        assertFalse(model.canUse(oldProduct))
        assertTrue(model.canUse(newProduct))
        coVerify(exactly = 1) { repository.read(52, "store-b") }
    }

    private suspend fun model(): ProductControlViewModel {
        session.login("fixture@example.test", "fixture")
        profile.load()
        return ProductControlViewModel(repository, profile, session).also {
            viewModels.put("product-control", it)
        }
    }

    private fun user(id: Long, code: String) = UserProfile(
        "user-$code",
        "Fixture",
        "fixture@example.test",
        UserRole.EMPLOYEE,
        UserStatus.ACTIVE,
        null,
        id,
        "Store $code",
        code,
    )

    private fun product(id: String, name: String, flow: StockFlow?) = AlertProduct(
        id = id,
        name = name,
        category = "Category",
        stock = BigDecimal.ONE,
        expirationDays = null,
        flow = flow,
    )
}
