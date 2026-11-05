package com.quistock.quistock.data.location

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDeviceLocationProviderTests {
    private val context = com.quistock.quistock.RecordingContext()
    private val manager = FakeLocationReader()
    private var service: DeviceLocationReader? = manager
    private val provider = AndroidDeviceLocationProvider(context) { service }
    private val requested get() = manager.requested
    private var newFix: Location?
        get() = manager.newFix
        set(value) {
            manager.newFix = value
        }

    @Before fun setup() {
        grant(fine = true, coarse = true)
    }

    @Test fun preciseOrApproximateAccessCountsAsPermission() {
        assertTrue(provider.hasPermission())
        grant(fine = false, coarse = true)
        assertTrue(provider.hasPermission())
        grant(fine = false, coarse = false)
        assertFalse(provider.hasPermission())
    }

    @Test fun withoutPermissionTheDeviceIsNotRead() = runBlocking {
        grant(fine = false, coarse = false)

        assertNull(provider.current())
        assertEquals(0, manager.enabledReads)
    }

    @Test fun platformConstructorAlsoRespectsDeniedPermission() = runBlocking {
        grant(fine = false, coarse = false)

        assertNull(AndroidDeviceLocationProvider(context).current())
    }

    @Test fun missingLocationServiceOrDisabledLocationGivesNoPosition() = runBlocking {
        manager.enabled = false
        assertNull(provider.current())

        service = null
        assertNull(provider.current())
    }

    @Test fun recentCachedFixIsUsedWithoutRequestingANewOne() = runBlocking {
        val now = System.currentTimeMillis()
        manager.cached[LocationManager.NETWORK_PROVIDER] = fix(1.0, 2.0, now - 5_000)
        manager.cached[LocationManager.GPS_PROVIDER] = fix(3.0, 4.0, now - 1_000)

        val point = provider.current()

        assertEquals(3.0, point?.latitude)
        assertEquals(4.0, point?.longitude)
        assertTrue(requested.isEmpty())
    }

    @Test fun staleCachedFixRequestsANewFixFromTheFastestProvider() = runBlocking {
        ALL_PROVIDERS.forEach { manager.cached[it] = fix(1.0, 2.0, STALE_TIME) }
        newFix = fix(5.0, 6.0, System.currentTimeMillis())

        val point = provider.current()

        assertEquals(5.0, point?.latitude)
        assertEquals(LocationManager.FUSED_PROVIDER, requested.first())
    }

    @Test fun providerWithoutAnAnswerFallsBackToAnotherProvider() = runBlocking {
        manager.fixes[LocationManager.FUSED_PROVIDER] = null
        manager.fixes[LocationManager.GPS_PROVIDER] = null
        manager.fixes[LocationManager.NETWORK_PROVIDER] = fix(9.0, 10.0, System.currentTimeMillis())

        assertEquals(9.0, provider.current()?.latitude)
    }

    @Test fun providerStillWaitingDoesNotDelayAnotherProvidersAnswer() = runBlocking {
        manager.waiting += LocationManager.FUSED_PROVIDER
        manager.waiting += LocationManager.GPS_PROVIDER
        manager.fixes[LocationManager.NETWORK_PROVIDER] = fix(11.0, 12.0, System.currentTimeMillis())

        assertEquals(11.0, withTimeout(2_000) { provider.current() }?.latitude)
    }

    @Test fun staleCachedFixIsTheFallbackWhenNoNewFixArrives() = runBlocking {
        manager.cached[LocationManager.NETWORK_PROVIDER] = fix(1.0, 2.0, STALE_TIME)

        val point = provider.current()

        assertEquals(1.0, point?.latitude)
        assertEquals(2.0, point?.longitude)
    }

    @Test fun approximateAccessSkipsGpsAndDisabledProviders() = runBlocking {
        grant(fine = false, coarse = true)
        manager.disabled += LocationManager.FUSED_PROVIDER
        newFix = fix(7.0, 8.0, System.currentTimeMillis())

        assertEquals(7.0, provider.current()?.latitude)
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), requested)
        assertFalse(LocationManager.GPS_PROVIDER in manager.cachedReads)
    }

    @Test fun noUsableProviderGivesNoPosition() = runBlocking {
        manager.allProviders = emptyList()

        assertNull(provider.current())
        assertTrue(requested.isEmpty())
    }

    @Test fun accessRevokedDuringTheReadGivesNoPosition() = runBlocking {
        manager.readFailure = SecurityException("revoked")

        assertNull(provider.current())
    }

    private fun grant(fine: Boolean, coarse: Boolean) {
        context.permissions[Manifest.permission.ACCESS_FINE_LOCATION] = status(fine)
        context.permissions[Manifest.permission.ACCESS_COARSE_LOCATION] = status(coarse)
    }

    private fun status(granted: Boolean) =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED

    private fun fix(latitude: Double, longitude: Double, time: Long) = Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
        this.time = time
    }

    private class FakeLocationReader : DeviceLocationReader {
        override var allProviders: List<String> = ALL_PROVIDERS
        var enabled = true
        var enabledReads = 0
        val disabled = mutableSetOf<String>()
        val cached = mutableMapOf<String, Location>()
        val cachedReads = mutableListOf<String>()
        val requested = mutableListOf<String>()
        var newFix: Location? = null
        val fixes = mutableMapOf<String, Location?>()
        val waiting = mutableSetOf<String>()
        var readFailure: Exception? = null

        override fun isLocationEnabled(): Boolean {
            enabledReads++
            return enabled
        }

        override fun isProviderEnabled(provider: String): Boolean = provider !in disabled

        override fun lastKnown(provider: String): Location? {
            cachedReads += provider
            readFailure?.let { throw it }
            return cached[provider]
        }

        override suspend fun request(provider: String): Location? {
            requested += provider
            if (provider in waiting) awaitCancellation()
            return if (provider in fixes) fixes[provider] else newFix
        }
    }

    private companion object {
        const val STALE_TIME = 1_000L
        val ALL_PROVIDERS = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
    }
}
