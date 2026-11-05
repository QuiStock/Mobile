package com.quistock.quistock.profile

import android.content.Context
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.ThemePreferenceRule
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.common.ThemeManager
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileThemeToggleTests {
    private val auth = mockk<AuthRepository>(relaxed = true)

    @get:Rule val authenticatedSession = AuthenticatedSessionRule(auth = auth)

    @get:Rule val themePreference = ThemePreferenceRule()

    @Test fun themeToggleSwitchesThemeAndBack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initial = ThemeManager.isDarkMode(context)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitDestination(scenario, R.id.homeFragment)
            onView(withId(R.id.btnPerfil)).perform(click())
            awaitDestination(scenario, R.id.profileFragment)

            onView(withId(R.id.btnThemeToggle)).perform(click())
            assertEquals(!initial, ThemeManager.isDarkMode(context))
            awaitDestination(scenario, R.id.profileFragment)

            onView(withId(R.id.btnThemeToggle)).perform(click())
            assertEquals(initial, ThemeManager.isDarkMode(context))
            awaitDestination(scenario, R.id.profileFragment)
            scenario.onActivity {
                val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                assertEquals(R.id.profileFragment, host.navController.currentDestination?.id)
            }
        }
    }
}
