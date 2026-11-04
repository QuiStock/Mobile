package com.quistock.quistock

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry

/** Uses real framework metadata while recording the platform calls a test owns. */
class RecordingContext : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
    val permissions = mutableMapOf<String, Int>()
    val started = mutableListOf<Intent>()
    var onStart: (Intent) -> Unit = {}

    override fun getPackageName(): String = "com.quistock.fixture"

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
        permissions[permission] ?: PackageManager.PERMISSION_DENIED

    override fun startActivity(intent: Intent) {
        started += intent
        onStart(intent)
    }
}
