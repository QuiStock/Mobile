package com.quistock.quistock.presentation.notifications

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.quistock.quistock.domain.port.NotificationPromptPreferences

fun needsNotificationPermission(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
    PackageManager.PERMISSION_GRANTED

/** Registers the Android 13+ notification permission request; [onResult] receives whether it was granted. */
fun ActivityResultCaller.notificationPermissionLauncher(onResult: (Boolean) -> Unit): ActivityResultLauncher<String> =
    registerForActivityResult(ActivityResultContracts.RequestPermission(), onResult)

fun ActivityResultLauncher<String>.requestNotifications() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launch(Manifest.permission.POST_NOTIFICATIONS)
}

/** Opens the app's notification settings (also where a permanently denied permission is granted). */
fun openNotificationSettings(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Asks for the notification permission once, the first time a mobile profile is ready.
 * Create it in `onCreate`, before the activity starts.
 */
class NotificationPermissionPrompt(
    private val activity: ComponentActivity,
    private val preferences: NotificationPromptPreferences,
    onGranted: () -> Unit,
) {
    private val launcher = activity.notificationPermissionLauncher { granted -> if (granted) onGranted() }

    fun askOnce() {
        if (preferences.wasAsked() || !needsNotificationPermission(activity)) return
        preferences.markAsked()
        launcher.requestNotifications()
    }
}
