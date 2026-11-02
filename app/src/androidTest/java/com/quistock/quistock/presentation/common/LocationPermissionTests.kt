package com.quistock.quistock.presentation.common

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.domain.port.LocationPromptPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationPermissionTests {
    @Test fun eitherPreciseOrApproximateAccessIsEnough() {
        val context = mockk<Context>()
        grant(context, fine = false, coarse = true)
        assertTrue(hasLocationPermission(context))
        grant(context, fine = true, coarse = false)
        assertTrue(hasLocationPermission(context))
        grant(context, fine = false, coarse = false)
        assertFalse(hasLocationPermission(context))
    }

    @Test fun appSettingsOpenForThisPackage() {
        val context = mockk<Context>()
        val intent = slot<Intent>()
        every { context.packageName } returns PACKAGE
        every { context.startActivity(capture(intent)) } just runs

        assertTrue(openAppSettings(context))
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.captured.action)
        assertEquals("package:$PACKAGE", intent.captured.data.toString())
    }

    @Test fun missingSettingsScreenIsReported() {
        val context = mockk<Context>()
        every { context.packageName } returns PACKAGE
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        assertFalse(openAppSettings(context))
    }

    @Test fun firstLaunchWithoutPermissionAsksOnce() {
        val (activity, launcher) = activityWithLauncher()
        grant(activity, fine = false, coarse = false)
        val preferences = preferences(asked = false)

        FirstLaunchLocationPrompt(activity, preferences).askOnFirstLaunch()

        verify(exactly = 1) { preferences.markAsked() }
        verify(exactly = 1) { launcher.launch(match { it.contentEquals(LOCATION_PERMISSIONS) }) }
    }

    @Test fun grantedPermissionIsNotRequestedAgain() {
        val (activity, launcher) = activityWithLauncher()
        grant(activity, fine = true, coarse = true)
        val preferences = preferences(asked = false)

        FirstLaunchLocationPrompt(activity, preferences).askOnFirstLaunch()

        verify(exactly = 1) { preferences.markAsked() }
        verify(exactly = 0) { launcher.launch(any()) }
    }

    @Test fun laterLaunchesDoNotAsk() {
        val (activity, launcher) = activityWithLauncher()
        val preferences = preferences(asked = true)

        FirstLaunchLocationPrompt(activity, preferences).askOnFirstLaunch()

        verify(exactly = 0) { preferences.markAsked() }
        verify(exactly = 0) { launcher.launch(any()) }
    }

    private fun activityWithLauncher(): Pair<ComponentActivity, ActivityResultLauncher<Array<String>>> {
        val activity = mockk<ComponentActivity>()
        val launcher = mockk<ActivityResultLauncher<Array<String>>>(relaxed = true)
        every {
            activity.registerForActivityResult(
                any<ActivityResultContract<Array<String>, Map<String, Boolean>>>(),
                any<ActivityResultCallback<Map<String, Boolean>>>(),
            )
        } returns launcher
        return activity to launcher
    }

    private fun preferences(asked: Boolean) = mockk<LocationPromptPreferences>(relaxed = true).also {
        every { it.wasAsked() } returns asked
    }

    private fun grant(context: Context, fine: Boolean, coarse: Boolean) {
        every { context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, any(), any()) } returns status(fine)
        every { context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, any(), any()) } returns
            status(coarse)
    }

    private fun status(granted: Boolean) =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED

    private companion object {
        const val PACKAGE = "com.quistock.fixture"
    }
}
