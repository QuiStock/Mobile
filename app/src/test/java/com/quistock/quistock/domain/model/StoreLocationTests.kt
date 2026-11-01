package com.quistock.quistock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreLocationTests {
    private fun store(
        street: String? = "Avenida Fixture, 1",
        city: String? = "Cidade",
        state: String? = "SP",
        lat: Double? = null,
        lng: Double? = null,
    ) = StoreLocation("Loja Fixture", street, city, state, lat, lng)

    @Test fun coordinatesTakePrecedenceForTheMapsQuery() {
        assertEquals("-23.5,-46.6", store(lat = -23.5, lng = -46.6).mapsQuery)
    }

    @Test fun addressIsUsedWhenThereAreNoCoordinates() {
        val location = store()
        assertFalse(location.hasCoordinates)
        assertEquals("Avenida Fixture, 1, Cidade - SP", location.mapsQuery)
    }

    @Test fun addressLinesOmitBlankParts() {
        assertEquals(listOf("Avenida Fixture, 1", "Cidade"), store(state = null).addressLines)
        assertEquals(listOf("Cidade - SP"), store(street = null).addressLines)
    }

    @Test fun withoutAddressOrCoordinatesThereIsNoQuery() {
        val location = store(street = null, city = null, state = null)
        assertFalse(location.hasAddress)
        assertNull(location.mapsQuery)
        assertTrue(store(street = null, city = null, state = null, lat = 1.0, lng = 2.0).hasCoordinates)
    }
}
