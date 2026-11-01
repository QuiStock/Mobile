package com.quistock.quistock.presentation.profile

import com.quistock.quistock.domain.model.StoreLocation
import com.quistock.quistock.domain.model.StoreLocationResult
import com.quistock.quistock.domain.port.StoreLocationRepository
import com.quistock.quistock.domain.usecase.LoadStoreLocationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreLocationViewModelTests {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<StoreLocationRepository>()
    private val location = StoreLocation("Loja", "Rua", "Cidade", "SP", -23.5, -46.6)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun model() = StoreLocationViewModel(LoadStoreLocationUseCase(repository, mockk(relaxed = true)))

    @Test fun loadsReadyAfterLoading() {
        coEvery { repository.read(7) } returns StoreLocationResult.Success(location)
        val model = model()
        model.load(7)
        assertSame(StoreLocationState.Loading, model.state.value)
        dispatcher.scheduler.runCurrent()
        assertSame(location, (model.state.value as StoreLocationState.Ready).location)
    }

    @Test fun mapsNoAddressAndFailure() {
        coEvery { repository.read(1) } returns StoreLocationResult.NoAddress
        coEvery { repository.read(2) } returns StoreLocationResult.Failure
        val model = model()
        model.load(1)
        dispatcher.scheduler.runCurrent()
        assertSame(StoreLocationState.NoAddress, model.state.value)
        model.load(2)
        dispatcher.scheduler.runCurrent()
        assertSame(StoreLocationState.Failure, model.state.value)
    }

    @Test fun sameStoreIsNotReloaded() {
        coEvery { repository.read(7) } returns StoreLocationResult.Success(location)
        val model = model()
        model.load(7)
        dispatcher.scheduler.runCurrent()
        model.load(7)
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.read(7) }
    }

    @Test fun retryReloadsOnlyAfterFailure() {
        coEvery { repository.read(7) } returnsMany listOf(
            StoreLocationResult.Failure,
            StoreLocationResult.Success(location),
        )
        val model = model()
        model.load(7)
        dispatcher.scheduler.runCurrent()
        model.retry()
        dispatcher.scheduler.runCurrent()
        assertTrue(model.state.value is StoreLocationState.Ready)
        model.retry()
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 2) { repository.read(7) }
    }
}
