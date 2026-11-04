package com.quistock.quistock.data.geocoding

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
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
        answer(listOf(address(-23.5, -46.6), address(1.0, 1.0)))

        val point = geocoder.locate(ADDRESS)

        assertEquals(-23.5, point?.latitude)
        assertEquals(-46.6, point?.longitude)
    }

    @Test fun noMatchOrGeocoderErrorGivesNoPoint() = runBlocking {
        answer(emptyList())
        assertNull(geocoder.locate(ADDRESS))

        answer(null)
        assertNull(geocoder.locate(ADDRESS))
    }

    @Test fun failingLookupGivesNoPoint() = runBlocking {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.fail(ADDRESS)
        } else {
            @Suppress("DEPRECATION")
            every { anyConstructed<Geocoder>().getFromLocationName(ADDRESS, 1) } throws
                IllegalArgumentException("bad address")
        }

        assertNull(geocoder.locate(ADDRESS))
    }

    @Test fun deviceWithoutGeocoderIsNotQueried() = runBlocking {
        every { Geocoder.isPresent() } returns false

        assertNull(geocoder.locate(ADDRESS))
    }

    @Suppress("DEPRECATION")
    private fun answer(addresses: List<Address>?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.answer(ADDRESS, addresses)
        } else {
            every { anyConstructed<Geocoder>().getFromLocationName(ADDRESS, 1) } returns addresses?.toMutableList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        fun answer(address: String, addresses: List<Address>?) {
            every {
                anyConstructed<Geocoder>().getFromLocationName(address, 1, any<Geocoder.GeocodeListener>())
            } answers {
                val listener = thirdArg<Geocoder.GeocodeListener>()
                if (addresses == null) {
                    listener.onError("unavailable")
                } else {
                    listener.onGeocode(addresses.toMutableList())
                }
            }
        }

        fun fail(address: String) {
            every { anyConstructed<Geocoder>().getFromLocationName(address, 1, any<Geocoder.GeocodeListener>()) } throws
                IllegalArgumentException("bad address")
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
