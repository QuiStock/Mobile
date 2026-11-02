package com.quistock.quistock.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.port.DeviceLocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Reads a single foreground fix from the platform [LocationManager]; no background tracking. */
class AndroidDeviceLocationProvider(private val context: Context) : DeviceLocationProvider {
    override fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun current(): GeoPoint? {
        val manager = context.getSystemService(LocationManager::class.java)
        if (!hasPermission() || manager == null) return null
        return try {
            locate(manager)?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // SecurityException if access is revoked mid-call, or a provider-specific failure.
            null
        }
    }

    /** A recent cached fix when there is one, else a new fix, else the last cached fix of any age. */
    private suspend fun locate(manager: LocationManager): Location? {
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val providers = enabledProviders(manager)
        val lastKnown = providers.mapNotNull { lastKnown(manager, it) }.maxByOrNull { it.time }
        val fresh = lastKnown?.takeIf { System.currentTimeMillis() - it.time <= FRESH_MILLIS }
        return fresh ?: providers.firstOrNull()?.let { request(manager, it) } ?: lastKnown
    }

    /** Fastest first: the fused provider when present, then network, then GPS (only with precise access). */
    private fun enabledProviders(manager: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
    }.filter { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }

    @SuppressLint("MissingPermission") // Checked by hasPermission() in current().
    private fun lastKnown(manager: LocationManager, provider: String): Location? =
        manager.getLastKnownLocation(provider)

    @SuppressLint("MissingPermission") // Checked by hasPermission() in current().
    private suspend fun request(manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location -> if (continuation.isActive) continuation.resume(location) }
            }
        }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val FRESH_MILLIS = 2 * 60_000L
    }
}
