package com.quistock.quistock.navigation

import android.widget.LinearLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.presentation.activity.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagerBottomNavigationTests {
    @get:Rule val session = AuthenticatedSessionRule(UserRole.MANAGER)

    @Test fun managerHasApprovedOrderAndDedicatedInterferencesDestination() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            scenario.onActivity { activity ->
                val bar = activity.findViewById<LinearLayout>(R.id.main_bottom_navigation)
                val actual = (0 until bar.childCount).map { bar.getChildAt(it).id }
                assertEquals(
                    listOf(
                        R.id.navigation_home,
                        R.id.navigation_interferences,
                        R.id.navigation_products,
                        R.id.navigation_employees,
                        R.id.navigation_chatbot,
                    ),
                    actual,
                )
            }
            onView(withId(R.id.navigation_interferences)).perform(click())
            awaitNavigationReady(scenario, R.id.managerInterferencesFragment)
            onView(withId(R.id.navigation_employees)).perform(click())
            awaitNavigationReady(scenario, R.id.employeeFragment)
            onView(withId(R.id.main_bottom_navigation)).check(matches(isDisplayed()))
        }
    }

    @Test fun managerAnalysisAndEmployeeRegistrationHideTheBar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            val hidden = listOf(R.id.managerOrderDetailFragment, R.id.employeeDetailFragment)
            hidden.forEach { destination ->
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        as NavHostFragment
                    host.navController.navigate(destination)
                }
                awaitNavigationReady(scenario, destination)
                onView(withId(R.id.main_bottom_navigation)).check(matches(withEffectiveVisibility(GONE)))
            }
        }
    }
}
