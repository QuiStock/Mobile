package com.quistock.quistock.presentation.home

import androidx.lifecycle.ViewModelStore
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummary
import com.quistock.quistock.domain.model.HomeSummaryResult
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.SummaryFailure
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.HomeSummaryCache
import com.quistock.quistock.domain.port.HomeSummaryRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.port.ProfileRepository
import com.quistock.quistock.domain.usecase.GetHomeSummaryUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeIndicatorsViewModelTests {
    private val store = ViewModelStore()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val session = SessionUseCase(auth)
    private val profiles = mockk<ProfileRepository>()
    private val profile = ProfileUseCase(profiles, session)
    private val remote = mockk<HomeSummaryRepository>()
    private val cache = mockk<HomeSummaryCache>()
    private val alerts = mockk<PriorityAlertsRepository>()
    private val summary = GetHomeSummaryUseCase(remote, cache, session, profile)
    private var scope = SessionBinding("session", "user", 51, System.currentTimeMillis() + 60_000)
    private val saved = CachedHomeSummary(HomeSummary(4, 2, 3, 5), System.currentTimeMillis() - 1000)

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { auth.hasAccess(any()) } returns true
        every { auth.currentBinding(any()) } answers { scope }
        coEvery { auth.bindProfile(any(), any(), any()) } coAnswers {
            scope = scope.copy(userId = secondArg(), storeId = thirdArg())
            scope
        }
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        coEvery { profiles.read() } returns user(51)
        coEvery { remote.read(any()) } returns HomeSummaryResult.Success(HomeSummary(0, 0, 0, 0))
        coEvery { cache.read(any()) } returns saved
        coEvery { cache.write(any(), any()) } returns true
        coEvery { alerts.read(any(), any()) } returns AlertProductsResult.Success(emptyList(), 0)
    }

    @After fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun recreationSharesWorkAndManualRetryPreservesSavedCountsUntilSuccess() = runTest {
        val pending = CompletableDeferred<HomeSummaryResult>()
        coEvery { remote.read(51) } coAnswers { pending.await() }
        val model = model()
        model.enter()
        runCurrent()
        assertTrue(model.summary.value.loading)
        assertEquals(saved, model.summary.value.cached)
        model.enter()
        runCurrent()
        coVerify(exactly = 1) { remote.read(51) }
        pending.complete(HomeSummaryResult.Failure(SummaryFailure.SERVER))
        runCurrent()
        assertEquals(SummaryFailure.SERVER, model.summary.value.failure)
        assertEquals(saved.receivedAt, model.summary.value.cached?.receivedAt)
        coEvery { remote.read(51) } returns HomeSummaryResult.Success(HomeSummary(0, 0, 0, 0))
        model.retrySummary()
        runCurrent()
        assertEquals(HomeSummary(0, 0, 0, 0), model.summary.value.cached?.summary)
        assertNull(model.summary.value.failure)
    }

    @Test fun eachNewEntryRevalidatesTheStoreAndNeverDisplaysPreviousStoreCounts() = runTest {
        val model = model()
        model.enter()
        runCurrent()
        assertNotNull(model.summary.value.cached)
        model.leave()
        coEvery { profiles.read() } returns user(52)
        coEvery { cache.read(any()) } returns null
        coEvery { remote.read(52) } returns HomeSummaryResult.Failure(SummaryFailure.SERVER)
        model.enter()
        runCurrent()
        coVerify(exactly = 1) { remote.read(52) }
        assertNull(model.summary.value.cached)
    }

    @Test fun offlineEntryKeepsOnlySavedCountsAndExpiryClearsThem() = runTest {
        val model = model()
        session.local.openOffline(session.snapshot(), scope, SessionFailure.NETWORK)
        profile.clear()
        model.enter()
        runCurrent()
        assertTrue(model.summary.value.offline)
        assertEquals(saved, model.summary.value.cached)
        assertFalse(model.canNavigate())
        coVerify(exactly = 0) { remote.read(any()) }
        scope = scope.copy(expiresAt = 1)
        model.tickSummary()
        runCurrent()
        assertEquals(SessionState.Expired, session.state.value)
        assertNull(model.summary.value.cached)
    }

    @Test fun cooldownBlocksRepeatedRequestsAndLogoutInvalidatesTheDisplay() = runTest {
        val deadline = System.currentTimeMillis() + 60_000
        coEvery { remote.read(51) } returns HomeSummaryResult.Failure(SummaryFailure.RATE_LIMIT, deadline)
        val model = model()
        model.enter()
        runCurrent()
        model.retrySummary()
        model.tickSummary()
        runCurrent()
        assertEquals(deadline, model.summary.value.retryAt)
        assertTrue(model.nextSummaryTickMillis() > 0)
        coVerify(exactly = 1) { remote.read(51) }
        session.logout()
        runCurrent()
        assertNull(model.summary.value.cached)
    }

    private suspend fun model(): HomeViewModel {
        session.login("fixture@example.test", "fixture")
        profile.load()
        return HomeViewModel(LoadPriorityAlertsUseCase(alerts), profile, session, summary).also { store.put("home", it) }
    }

    private fun user(store: Long) = ProfileResult.Success(
        UserProfile("user", "Fixture", "fixture@example.test", UserRole.EMPLOYEE, UserStatus.ACTIVE, null, store, "Store"),
    )
}
