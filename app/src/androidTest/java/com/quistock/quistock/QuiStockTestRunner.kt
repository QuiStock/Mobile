package com.quistock.quistock

import android.Manifest
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner

/**
 * Grants foreground location before any test starts, so the first-launch permission request in
 * MainActivity never opens a system dialog that would steal window focus from Espresso.
 */
class QuiStockTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val target = targetContext.packageName
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).forEach {
            // Reading the output waits for the shell command to finish.
            ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("pm grant $target $it"))
                .use { stream -> stream.readBytes() }
        }
    }
}
