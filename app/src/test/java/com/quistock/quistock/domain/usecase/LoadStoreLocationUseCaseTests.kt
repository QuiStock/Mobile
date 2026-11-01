package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.model.StoreLocation
import com.quistock.quistock.domain.model.StoreLocationResult
import com.quistock.quistock.domain.port.AddressGeocoder
import com.quistock.quistock.domain.port.StoreLocationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LoadStoreLocationUseCaseTests {
    private val repository = mockk<StoreLocationRepository>()
    private val geocoder = mockk<AddressGeocoder>()
    private val useCase = LoadStoreLocationUseCase(repository, geocoder)

    private fun store(street: String? = "Rua", lat: Double? = null, lng: Double? = null) =
        StoreLocation("Loja", street, "Cidade", "SP", lat, lng)

    @Test fun addressWithoutCoordinatesGetsAPreviewPointButKeepsTheAddressQuery() = runTest {
        val location = store()
        coEvery { repository.read(7) } returns StoreLocationResult.Success(location)
        coEvery { geocoder.locate(any()) } returns GeoPoint(-23.5, -46.6)
        val result = (useCase(7) as StoreLocationResult.Success).location
        assertEquals(-23.5, result.mapPoint!!.latitude, 0.0)
        assertEquals(location.mapsQuery, result.mapsQuery)
        assertEquals(false, result.hasCoordinates)
    }

    @Test fun failedGeocodingKeepsTheLocationWithoutPreview() = runTest {
        coEvery { repository.read(7) } returns StoreLocationResult.Success(store())
        coEvery { geocoder.locate(any()) } returns null
        assertNull((useCase(7) as StoreLocationResult.Success).location.mapPoint)
    }

    @Test fun exactCoordinatesAreNotGeocoded() = runTest {
        coEvery { repository.read(7) } returns StoreLocationResult.Success(store(lat = 1.0, lng = 2.0))
        val result = (useCase(7) as StoreLocationResult.Success).location
        assertEquals(1.0, result.mapPoint!!.latitude, 0.0)
        coVerify(exactly = 0) { geocoder.locate(any()) }
    }

    @Test fun otherResultsPassThrough() = runTest {
        coEvery { repository.read(1) } returns StoreLocationResult.NoAddress
        coEvery { repository.read(2) } returns StoreLocationResult.Failure
        assertSame(StoreLocationResult.NoAddress, useCase(1))
        assertSame(StoreLocationResult.Failure, useCase(2))
        coVerify(exactly = 0) { geocoder.locate(any()) }
    }
}
