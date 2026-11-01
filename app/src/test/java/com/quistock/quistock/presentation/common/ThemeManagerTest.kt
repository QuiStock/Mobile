package com.quistock.quistock.presentation.common

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class ThemeManagerTest {
    private val context = mockk<Context>()
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    @Before
    fun setup() {
        every {
            context.getSharedPreferences(ThemeManager.PREFS_NAME, Context.MODE_PRIVATE)
        } returns preferences
        every { preferences.edit() } returns editor
        every { preferences.getBoolean(ThemeManager.KEY_IS_DARK, true) } returns true
        every { editor.putBoolean(any(), any()) } returns editor
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    fun `should default to dark mode when no preference stored`() {
        ThemeManager.isDarkMode(context) shouldBe true
    }

    @Test
    fun `should return stored preference value`() {
        every { preferences.getBoolean(ThemeManager.KEY_IS_DARK, true) } returns false

        ThemeManager.isDarkMode(context) shouldBe false
    }

    @Test
    fun `should toggle from dark to light mode`() {
        every { preferences.getBoolean(ThemeManager.KEY_IS_DARK, true) } returns true

        val result = ThemeManager.toggleTheme(context)

        result shouldBe false
        verify { editor.putBoolean(ThemeManager.KEY_IS_DARK, false) }
        verify { editor.apply() }
        AppCompatDelegate.getDefaultNightMode() shouldBe AppCompatDelegate.MODE_NIGHT_NO
    }

    @Test
    fun `should toggle from light to dark mode`() {
        every { preferences.getBoolean(ThemeManager.KEY_IS_DARK, true) } returns false

        val result = ThemeManager.toggleTheme(context)

        result shouldBe true
        verify { editor.putBoolean(ThemeManager.KEY_IS_DARK, true) }
        verify { editor.apply() }
        AppCompatDelegate.getDefaultNightMode() shouldBe AppCompatDelegate.MODE_NIGHT_YES
    }

    @Test
    fun `should apply night mode on init`() {
        every { preferences.getBoolean(ThemeManager.KEY_IS_DARK, true) } returns true

        ThemeManager.init(context)

        AppCompatDelegate.getDefaultNightMode() shouldBe AppCompatDelegate.MODE_NIGHT_YES
    }
}
