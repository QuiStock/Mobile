package com.quistock.quistock.onboarding

import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.R
import com.quistock.quistock.presentation.activity.MainActivity
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFragmentTests {

    @Test
    fun onboarding_displaysUpdatedDescriptionAndNavigatesToLogin() {
        lateinit var navController: NavController

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navHost = activity.binding.navHostFragment
                navController = navHost.findNavController()
                navController.navigate(R.id.onboardingFragment)
            }

            onView(withId(R.id.onboarding_description))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.onboarding_description)))

            onView(withId(R.id.onboarding_continue_button)).perform(click())

            scenario.onActivity {
                assertThat(navController.currentDestination?.id, `is`(R.id.loginFragment))
            }
        }
    }
}
