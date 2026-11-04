package com.quistock.quistock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner

/**
 * Grants foreground location and (on API 33+) notifications before tests start, so first-launch
 * permission dialogs do not pause MainActivity while Espresso waits for a resumed destination.
 */
class QuiStockTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val target = targetContext.packageName
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.forEach { permission ->
            // Reading the output waits for the shell command to finish.
            ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("pm grant $target $permission"))
                .use { stream -> stream.readBytes() }
            check(targetContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                "Instrumentation setup could not grant $permission"
            }
        }
    }
}
