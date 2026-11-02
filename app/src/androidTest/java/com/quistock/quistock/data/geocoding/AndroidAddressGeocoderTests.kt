package com.quistock.quistock.data.geocoding

import android.location.Address
import android.location.Geocoder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AndroidAddressGeocoderTests {
    private val geocoder = AndroidAddressGeocoder(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before fun setup() {
        mockkStatic(Geocoder::class)
        mockkConstructor(Geocoder::class)
        every { Geocoder.isPresent() } returns true
    }

    @After fun cleanup() = unmockkAll()

    @Test fun firstMatchBecomesThePoint() = runBlocking {
        answer { it.onGeocode(mutableListOf(address(-23.5, -46.6), address(1.0, 1.0))) }

        val point = geocoder.locate(ADDRESS)

        assertEquals(-23.5, point?.latitude)
        assertEquals(-46.6, point?.longitude)
    }

    @Test fun noMatchOrGeocoderErrorGivesNoPoint() = runBlocking {
        answer { it.onGeocode(mutableListOf()) }
        assertNull(geocoder.locate(ADDRESS))

        answer { it.onError("unavailable") }
        assertNull(geocoder.locate(ADDRESS))
    }

    @Test fun failingLookupGivesNoPoint() = runBlocking {
        every { anyConstructed<Geocoder>().getFromLocationName(ADDRESS, 1, any<Geocoder.GeocodeListener>()) } throws
            IllegalArgumentException("bad address")

        assertNull(geocoder.locate(ADDRESS))
    }

    @Test fun deviceWithoutGeocoderIsNotQueried() = runBlocking {
        every { Geocoder.isPresent() } returns false

        assertNull(geocoder.locate(ADDRESS))
    }

    private fun answer(reply: (Geocoder.GeocodeListener) -> Unit) {
        every { anyConstructed<Geocoder>().getFromLocationName(ADDRESS, 1, any<Geocoder.GeocodeListener>()) } answers {
            reply(thirdArg())
        }
    }

    private fun address(latitude: Double, longitude: Double) = Address(Locale.ROOT).apply {
        this.latitude = latitude
        this.longitude = longitude
    }

    private companion object {
        const val ADDRESS = "Rua Fixture, 100, Cidade - SP"
    }
}
