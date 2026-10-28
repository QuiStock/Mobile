package com.quistock.quistock.home

import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.presentation.activity.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmployeeReviewNoticeTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()

    @Test
    fun employeeCannotOpenManagerRequestDetailsOrSeeTheirGuidance() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitDestination(scenario, R.id.homeFragment)
            onView(withId(R.id.managerHomeNotice)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            listOf(R.id.managerOrderDetailFragment, R.id.managerPromotionDetailFragment).forEach { destination ->
                scenario.onActivity { it.binding.navHostFragment.findNavController().navigate(destination) }
                awaitDestination(scenario, R.id.homeFragment)
                onView(withId(R.id.managerReviewNotice)).check(doesNotExist())
            }
        }
    }
}
