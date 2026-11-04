package com.quistock.quistock.presentation.common

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.quistock.quistock.domain.port.LocationPromptPreferences

/** Foreground location only; Android 12+ lets the user choose approximate instead of precise. */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/** Opens the app's system settings, where a permanently denied permission can still be granted. */
fun openAppSettings(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Shows the system location permission request the first time the app is opened. Create it in
 * `onCreate` (launchers must be registered before the activity starts) and call [askOnFirstLaunch].
 */
class FirstLaunchLocationPrompt internal constructor(
    private val context: Context,
    private val preferences: LocationPromptPreferences,
    private val launch: (Array<String>) -> Unit,
) {
    // The answer is read where the location is used (Profile), so the result needs no handling here.
    constructor(activity: ComponentActivity, preferences: LocationPromptPreferences) : this(
        activity,
        preferences,
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}.let { launcher ->
            { permissions: Array<String> -> launcher.launch(permissions) }
        },
    )

    fun askOnFirstLaunch() {
        if (preferences.wasAsked()) return
        preferences.markAsked()
        if (!hasLocationPermission(context)) launch(LOCATION_PERMISSIONS)
    }
}
