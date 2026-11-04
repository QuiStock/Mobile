package com.quistock.quistock.presentation.common

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.RecordingContext
import com.quistock.quistock.domain.port.LocationPromptPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationPermissionTests {
    @Test fun eitherPreciseOrApproximateAccessIsEnough() {
        val context = RecordingContext()
        grant(context, fine = false, coarse = true)
        assertTrue(hasLocationPermission(context))
        grant(context, fine = true, coarse = false)
        assertTrue(hasLocationPermission(context))
        grant(context, fine = false, coarse = false)
        assertFalse(hasLocationPermission(context))
    }

    @Test fun appSettingsOpenForThisPackage() {
        val context = RecordingContext()

        assertTrue(openAppSettings(context))
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context.started.single().action)
        assertEquals("package:$PACKAGE", context.started.single().data.toString())
    }

    @Test fun missingSettingsScreenIsReported() {
        val context = RecordingContext()
        context.onStart = { throw ActivityNotFoundException() }

        assertFalse(openAppSettings(context))
    }

    @Test fun firstLaunchWithoutPermissionAsksOnce() {
        val context = RecordingContext()
        val requests = mutableListOf<Array<String>>()
        grant(context, fine = false, coarse = false)
        val preferences = preferences(asked = false)

        val prompt = FirstLaunchLocationPrompt(context, preferences, requests::add)
        prompt.askOnFirstLaunch()
        every { preferences.wasAsked() } returns true
        prompt.askOnFirstLaunch()

        verify(exactly = 1) { preferences.markAsked() }
        assertTrue(requests.single().contentEquals(LOCATION_PERMISSIONS))
    }

    @Test fun grantedPermissionIsNotRequestedAgain() {
        val context = RecordingContext()
        val requests = mutableListOf<Array<String>>()
        grant(context, fine = true, coarse = true)
        val preferences = preferences(asked = false)

        FirstLaunchLocationPrompt(context, preferences, requests::add).askOnFirstLaunch()

        verify(exactly = 1) { preferences.markAsked() }
        assertTrue(requests.isEmpty())
    }

    @Test fun laterLaunchesDoNotAsk() {
        val context = RecordingContext()
        val requests = mutableListOf<Array<String>>()
        val preferences = preferences(asked = true)

        FirstLaunchLocationPrompt(context, preferences, requests::add).askOnFirstLaunch()

        verify(exactly = 0) { preferences.markAsked() }
        assertTrue(requests.isEmpty())
    }

    private fun preferences(asked: Boolean) = mockk<LocationPromptPreferences>(relaxed = true).also {
        every { it.wasAsked() } returns asked
    }

    private fun grant(context: RecordingContext, fine: Boolean, coarse: Boolean) {
        context.permissions[Manifest.permission.ACCESS_FINE_LOCATION] = status(fine)
        context.permissions[Manifest.permission.ACCESS_COARSE_LOCATION] = status(coarse)
    }

    private fun status(granted: Boolean) =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED

    private companion object {
        const val PACKAGE = "com.quistock.fixture"
    }
}
