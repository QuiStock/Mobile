package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.GeoPoint

interface AddressGeocoder {
    /** Returns the best match for [address], or null when none is found or the lookup fails. */
    suspend fun locate(address: String): GeoPoint?
}
