package com.quistock.quistock.presentation.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    const val PREFS_NAME = "quistock_theme_prefs"
    const val KEY_IS_DARK = "key_is_dark_mode"

    fun init(context: Context) {
        val isDark = isDarkMode(context)
        applyMode(isDark)
    }

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_DARK, true)
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_DARK, isDark).apply()
        applyMode(isDark)
    }

    fun toggleTheme(context: Context): Boolean {
        val nextMode = !isDarkMode(context)
        setDarkMode(context, nextMode)
        return nextMode
    }

    private fun applyMode(isDark: Boolean) {
        val target = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != target) {
            AppCompatDelegate.setDefaultNightMode(target)
        }
    }
}
