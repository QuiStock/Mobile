package com.quistock.quistock.presentation.home

import androidx.lifecycle.ViewModelStore
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.port.ProfileRepository
import com.quistock.quistock.domain.usecase.LoadPriorityAlertsUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTests {
    private val store = ViewModelStore()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val session = SessionUseCase(auth)
    private val repository = mockk<PriorityAlertsRepository>()
    private val profileRepository = mockk<ProfileRepository>()
    private val profile = ProfileUseCase(profileRepository, session)

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        coEvery { profileRepository.read() } returns ProfileResult.Success(
            UserProfile(
                "fixture", "Fixture", "fixture@example.test", UserRole.EMPLOYEE, UserStatus.ACTIVE,
                null, 17, "Fixture store", "store",
            ),
        )
        coEvery { repository.read(any(), any()) } returns validProducts()
    }

    @After
    fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `view recreation retains result while leaving and returning reloads`() = runTest {
        val model = createModel()
        model.enter()
        runCurrent()
        assertTrue(model.state.value is HomeAlertsState.Ready)
        model.enter()
        runCurrent()
        coVerify(exactly = 1) { repository.read(17, "store") }

        model.leave()
        model.enter()
        runCurrent()
        coVerify(exactly = 2) { repository.read(17, "store") }
    }

    @Test
    fun `failure waits for manual retry`() = runTest {
        coEvery { repository.read(any(), any()) } returnsMany listOf(AlertProductsResult.Failure, validProducts())
        val model = createModel()
        model.enter()
        runCurrent()
        assertEquals(HomeAlertsState.Error, model.state.value)
        model.enter()
        runCurrent()
        coVerify(exactly = 1) { repository.read(any(), any()) }

        model.retry()
        runCurrent()
        assertTrue(model.state.value is HomeAlertsState.Ready)
    }

    @Test
    fun `session invalidation discards pending alerts and blocks navigation`() = runTest {
        val pending = CompletableDeferred<AlertProductsResult>()
        coEvery { repository.read(any(), any()) } coAnswers { pending.await() }
        val model = createModel()
        model.enter()
        runCurrent()
        session.expire(session.snapshot())
        runCurrent()
        pending.complete(validProducts())
        runCurrent()

        assertEquals(HomeAlertsState.Loading, model.state.value)
        assertFalse(model.canNavigate())
    }

    private suspend fun createModel(): HomeViewModel {
        session.login("fixture@example.test", "fixture")
        profile.load()
        return HomeViewModel(LoadPriorityAlertsUseCase(repository), profile, session).also { store.put("home", it) }
    }

    private fun validProducts() = AlertProductsResult.Success(
        listOf(AlertProduct("product", "Fixture product", "Category", 5, 1, StockFlow.LOW)),
        invalidCount = 0,
    )
}
