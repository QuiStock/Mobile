package com.quistock.quistock.data.geocoding

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.port.AddressGeocoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class AndroidAddressGeocoder(private val context: Context) : AddressGeocoder {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun locate(address: String): GeoPoint? {
        if (!Geocoder.isPresent()) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MILLIS) { search(Geocoder(context, Locale.getDefault()), address) }
                ?.firstOrNull()
                ?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun search(geocoder: Geocoder, address: String): List<Address>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(
                    address,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses)

                        override fun onError(errorMessage: String?) = continuation.resume(null)
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) { geocoder.getFromLocationName(address, 1) }
        }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
