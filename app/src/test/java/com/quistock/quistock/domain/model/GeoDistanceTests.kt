package com.quistock.quistock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTests {
    @Test fun samePointIsZero() {
        val point = GeoPoint(-23.5614, -46.6559)
        assertEquals(0.0, point.distanceMetersTo(point), 0.0)
    }

    @Test fun oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111_195.0, GeoPoint(0.0, 0.0).distanceMetersTo(GeoPoint(1.0, 0.0)), 50.0)
    }

    @Test fun matchesKnownCityDistanceAndIsSymmetric() {
        // Praça da Sé (São Paulo) to Praça XV (Rio de Janeiro): about 361 km in a straight line.
        val saoPaulo = GeoPoint(-23.5505, -46.6333)
        val rio = GeoPoint(-22.9035, -43.1745)
        val distance = saoPaulo.distanceMetersTo(rio)
        assertEquals(361_000.0, distance, 2_000.0)
        assertEquals(distance, rio.distanceMetersTo(saoPaulo), 1e-6)
    }

    @Test fun antipodesDoNotOverflow() {
        assertEquals(20_015_000.0, GeoPoint(0.0, 0.0).distanceMetersTo(GeoPoint(0.0, 180.0)), 1_000.0)
    }
}
