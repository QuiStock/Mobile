package com.quistock.quistock.presentation.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceFormatterTests {
    @Test fun metersAreRoundedToTens() {
        assertEquals("0 m", DistanceFormatter.format(0.0))
        assertEquals("350 m", DistanceFormatter.format(347.0))
        assertEquals("990 m", DistanceFormatter.format(994.9))
    }

    @Test fun negativeDistanceIsZero() {
        assertEquals("0 m", DistanceFormatter.format(-5.0))
    }

    @Test fun kilometersUseOneDecimalWithComma() {
        assertEquals("1,0 km", DistanceFormatter.format(995.0))
        assertEquals("2,3 km", DistanceFormatter.format(2_340.0))
        assertEquals("99,5 km", DistanceFormatter.format(99_499.0))
    }

    @Test fun farDistancesUseWholeKilometers() {
        assertEquals("120 km", DistanceFormatter.format(120_400.0))
        assertEquals("1.200 km", DistanceFormatter.format(1_200_000.0))
    }
}
