package com.quistock.quistock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.presentation.common.ThemeManager
import org.junit.rules.ExternalResource

/** Restores the theme a test found, so that toggling it does not leak into later tests. */
class ThemePreferenceRule : ExternalResource() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var originalIsDark = true

    override fun before() {
        originalIsDark = ThemeManager.isDarkMode(context)
    }

    override fun after() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeManager.setDarkMode(context, originalIsDark)
        }
    }
}
