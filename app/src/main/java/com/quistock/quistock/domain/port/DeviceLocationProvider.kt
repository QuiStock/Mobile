package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.GeoPoint

interface DeviceLocationProvider {
    /** True when the user granted precise or approximate location access to the app. */
    fun hasPermission(): Boolean

    /** Returns the device's current position, or null without permission, with location off, or on timeout. */
    suspend fun current(): GeoPoint?
}
