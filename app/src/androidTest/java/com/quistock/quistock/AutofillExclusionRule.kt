package com.quistock.quistock

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * Keep the device autofill service (for example, an account saved in Google Password Manager)
 * from filling form fields or opening its overlay, which steals window focus from Espresso.
 *
 * Marking the views as not important for autofill is not enough: recent Android versions may still
 * request a fill for password and email fields. The service is therefore switched off for the
 * duration of each test and the device's original choice is restored afterwards.
 */
class AutofillExclusionRule : ExternalResource() {
    private var originalService: String? = null

    override fun before() {
        val current = shell("settings get secure $AUTOFILL_SERVICE_SETTING").trim()
        if (current.isNotEmpty() && current != NO_SERVICE) {
            originalService = current
            shell("settings put secure $AUTOFILL_SERVICE_SETTING $NO_SERVICE")
        }
    }

    override fun after() {
        originalService?.let { shell("settings put secure $AUTOFILL_SERVICE_SETTING $it") }
        originalService = null
    }

    private fun shell(command: String): String {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }
    }

    private companion object {
        const val AUTOFILL_SERVICE_SETTING = "autofill_service"
        const val NO_SERVICE = "null"
    }
}
