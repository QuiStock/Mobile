package com.quistock.quistock.presentation.profile

import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.port.DeviceLocationProvider
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
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserLocationViewModelTests {
    private val dispatcher = StandardTestDispatcher()
    private val provider = mockk<DeviceLocationProvider>()
    private val point = GeoPoint(-23.56, -46.65)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun withoutPermissionAsksForItAndDoesNotRead() {
        every { provider.hasPermission() } returns false
        val model = UserLocationViewModel(provider)
        model.refresh()
        dispatcher.scheduler.runCurrent()
        assertSame(UserLocationState.PermissionNeeded, model.state.value)
        coVerify(exactly = 0) { provider.current() }
    }

    @Test fun locatingThenLocated() {
        every { provider.hasPermission() } returns true
        coEvery { provider.current() } returns point
        val model = UserLocationViewModel(provider)
        model.refresh()
        assertSame(UserLocationState.Locating, model.state.value)
        dispatcher.scheduler.runCurrent()
        assertSame(point, (model.state.value as UserLocationState.Located).point)
    }

    @Test fun noFixIsUnavailableAndRefreshRetries() {
        every { provider.hasPermission() } returns true
        coEvery { provider.current() } returnsMany listOf(null, point)
        val model = UserLocationViewModel(provider)
        model.refresh()
        dispatcher.scheduler.runCurrent()
        assertSame(UserLocationState.Unavailable, model.state.value)
        model.refresh()
        dispatcher.scheduler.runCurrent()
        assertSame(point, (model.state.value as UserLocationState.Located).point)
    }

    @Test fun knownPositionStaysWhileRefreshingAndWhenRefreshFails() {
        every { provider.hasPermission() } returns true
        val pending = CompletableDeferred<GeoPoint?>()
        coEvery { provider.current() } returns point coAndThen { pending.await() }
        val model = UserLocationViewModel(provider)
        model.refresh()
        dispatcher.scheduler.runCurrent()
        model.refresh()
        dispatcher.scheduler.runCurrent()
        assertSame(point, (model.state.value as UserLocationState.Located).point)
        pending.complete(null)
        dispatcher.scheduler.runCurrent()
        assertSame(point, (model.state.value as UserLocationState.Located).point)
    }

    @Test fun refreshWhileReadingDoesNotStartAnotherRead() {
        every { provider.hasPermission() } returns true
        val pending = CompletableDeferred<GeoPoint?>()
        coEvery { provider.current() } coAnswers { pending.await() }
        val model = UserLocationViewModel(provider)
        model.refresh()
        dispatcher.scheduler.runCurrent()
        model.refresh()
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { provider.current() }
    }

    @Test fun revokedPermissionCancelsReadAndAsksAgain() {
        every { provider.hasPermission() } returnsMany listOf(true, false)
        val pending = CompletableDeferred<GeoPoint?>()
        coEvery { provider.current() } coAnswers { pending.await() }
        val model = UserLocationViewModel(provider)
        model.refresh()
        dispatcher.scheduler.runCurrent()
        model.refresh()
        pending.complete(point)
        dispatcher.scheduler.runCurrent()
        assertSame(UserLocationState.PermissionNeeded, model.state.value)
    }
}
