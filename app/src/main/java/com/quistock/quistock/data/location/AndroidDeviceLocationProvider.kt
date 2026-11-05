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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Reads a single foreground fix from the platform [LocationManager]; no background tracking. */
class AndroidDeviceLocationProvider internal constructor(
    private val context: Context,
    private val locationReader: () -> DeviceLocationReader?,
) : DeviceLocationProvider {
    constructor(context: Context) : this(
        context,
        { context.getSystemService(LocationManager::class.java)?.let { PlatformLocationReader(context, it) } },
    )

    override fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun current(): GeoPoint? {
        val manager = locationReader()
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
    private suspend fun locate(manager: DeviceLocationReader): Location? {
        if (!manager.isLocationEnabled()) return null
        val providers = enabledProviders(manager)
        val lastKnown = providers.mapNotNull { manager.lastKnown(it) }.maxByOrNull { it.time }
        val fresh = lastKnown?.takeIf { System.currentTimeMillis() - it.time <= FRESH_MILLIS }
        return fresh ?: firstFix(manager, providers) ?: lastKnown
    }

    /** Asks every provider at once: one can answer null or wait for a satellite while another has a position. */
    private suspend fun firstFix(manager: DeviceLocationReader, providers: List<String>): Location? =
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            channelFlow { providers.forEach { provider -> launch { send(manager.request(provider)) } } }
                .filterNotNull()
                .firstOrNull()
        }

    /** Fastest first: the fused provider when present, then network, then GPS (only with precise access). */
    private fun enabledProviders(manager: DeviceLocationReader): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
    }.filter { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val FRESH_MILLIS = 2 * 60_000L
    }
}

/** Narrow platform boundary so selection tests do not reflect over Android's LocationManager. */
internal interface DeviceLocationReader {
    val allProviders: List<String>
    fun isLocationEnabled(): Boolean
    fun isProviderEnabled(provider: String): Boolean
    fun lastKnown(provider: String): Location?
    suspend fun request(provider: String): Location?
}

private class PlatformLocationReader(private val context: Context, private val manager: LocationManager) :
    DeviceLocationReader {
    override val allProviders: List<String> get() = manager.allProviders
    override fun isLocationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(manager)
    override fun isProviderEnabled(provider: String): Boolean = manager.isProviderEnabled(provider)

    @SuppressLint("MissingPermission") // Provider checks foreground permission before reading.
    override fun lastKnown(provider: String): Location? = manager.getLastKnownLocation(provider)

    @SuppressLint("MissingPermission") // Provider checks foreground permission before reading.
    override suspend fun request(provider: String): Location? = suspendCancellableCoroutine { continuation ->
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
