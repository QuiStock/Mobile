package com.quistock.quistock.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_008.8

/** Great-circle (haversine) distance in meters; precise enough to show how far the user is from a store. */
fun GeoPoint.distanceMetersTo(other: GeoPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val deltaLat = lat2 - lat1
    val deltaLng = Math.toRadians(other.longitude - longitude)
    val a = sin(deltaLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(deltaLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
}
