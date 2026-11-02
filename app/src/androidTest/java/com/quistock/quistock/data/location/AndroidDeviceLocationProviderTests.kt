package com.quistock.quistock.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.function.Consumer

@RunWith(AndroidJUnit4::class)
class AndroidDeviceLocationProviderTests {
    private val context = mockk<Context>()
    private val manager = mockk<LocationManager>()
    private val provider = AndroidDeviceLocationProvider(context)
    private val requested = mutableListOf<String>()
    private var newFix: Location? = null

    @Before fun setup() {
        every { context.getSystemService(LocationManager::class.java) } returns manager
        every { context.mainExecutor } returns Executor { it.run() }
        grant(fine = true, coarse = true)
        every { manager.isLocationEnabled } returns true
        every { manager.allProviders } returns ALL_PROVIDERS
        every { manager.isProviderEnabled(any()) } returns true
        every { manager.getLastKnownLocation(any()) } returns null
        every {
            manager.getCurrentLocation(any<String>(), any<CancellationSignal>(), any(), any<Consumer<Location?>>())
        } answers {
            requested += firstArg<String>()
            arg<Executor>(2).execute { arg<Consumer<Location?>>(3).accept(newFix) }
        }
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
        verify(exactly = 0) { manager.isLocationEnabled }
    }

    @Test fun missingLocationServiceOrDisabledLocationGivesNoPosition() = runBlocking {
        every { manager.isLocationEnabled } returns false
        assertNull(provider.current())

        every { context.getSystemService(LocationManager::class.java) } returns null
        assertNull(provider.current())
    }

    @Test fun recentCachedFixIsUsedWithoutRequestingANewOne() = runBlocking {
        val now = System.currentTimeMillis()
        every { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } returns fix(1.0, 2.0, now - 5_000)
        every { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns fix(3.0, 4.0, now - 1_000)

        val point = provider.current()

        assertEquals(3.0, point?.latitude)
        assertEquals(4.0, point?.longitude)
        assertTrue(requested.isEmpty())
    }

    @Test fun staleCachedFixRequestsANewFixFromTheFastestProvider() = runBlocking {
        every { manager.getLastKnownLocation(any()) } returns fix(1.0, 2.0, STALE_TIME)
        newFix = fix(5.0, 6.0, System.currentTimeMillis())

        val point = provider.current()

        assertEquals(5.0, point?.latitude)
        assertEquals(listOf(LocationManager.FUSED_PROVIDER), requested)
    }

    @Test fun staleCachedFixIsTheFallbackWhenNoNewFixArrives() = runBlocking {
        every { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } returns fix(1.0, 2.0, STALE_TIME)

        val point = provider.current()

        assertEquals(1.0, point?.latitude)
        assertEquals(2.0, point?.longitude)
    }

    @Test fun approximateAccessSkipsGpsAndDisabledProviders() = runBlocking {
        grant(fine = false, coarse = true)
        every { manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) } returns false
        newFix = fix(7.0, 8.0, System.currentTimeMillis())

        assertEquals(7.0, provider.current()?.latitude)
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), requested)
        verify(exactly = 0) { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }
    }

    @Test fun noUsableProviderGivesNoPosition() = runBlocking {
        every { manager.allProviders } returns emptyList()

        assertNull(provider.current())
        assertTrue(requested.isEmpty())
    }

    @Test fun accessRevokedDuringTheReadGivesNoPosition() = runBlocking {
        every { manager.getLastKnownLocation(any()) } throws SecurityException("revoked")

        assertNull(provider.current())
    }

    private fun grant(fine: Boolean, coarse: Boolean) {
        every { context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, any(), any()) } returns status(fine)
        every { context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, any(), any()) } returns
            status(coarse)
    }

    private fun status(granted: Boolean) =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED

    private fun fix(latitude: Double, longitude: Double, time: Long) = Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
        this.time = time
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
