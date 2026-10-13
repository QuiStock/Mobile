package com.quistock.quistock.help

import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.R
import com.quistock.quistock.app.navigation.NavGraph
import com.quistock.quistock.presentation.activity.MainActivity
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaqFragmentTests {
    @Test
    fun faqCards_shouldExpandAndCollapseAnswersWhenTapped() = runInstrumented { _ ->
        val cards =
            listOf(
                R.id.faq_card_1 to R.id.faq_answer_1,
                R.id.faq_card_2 to R.id.faq_answer_2,
                R.id.faq_card_3 to R.id.faq_answer_3,
                R.id.faq_card_4 to R.id.faq_answer_4,
            )

        cards.forEach { (cardId, answerId) ->
            onView(withId(answerId)).check(matches(withEffectiveVisibility(GONE)))

            onView(withId(cardId)).perform(scrollTo(), click())
            onView(withId(answerId)).check(matches(withEffectiveVisibility(VISIBLE)))

            onView(withId(cardId)).perform(scrollTo(), click())
            onView(withId(answerId)).check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test
    fun contactAndBackControls_shouldNavigateBetweenFaqContactAndProfile() = runInstrumented { navController ->
        onView(withId(R.id.faq_contact_link)).perform(scrollTo(), click())
        assertDestination(navController, NavGraph.Destinations.CONTACT)
        listOf(
            R.id.contact_name_input,
            R.id.contact_email_input,
            R.id.contact_problem_input,
            R.id.contact_message_input,
            R.id.contact_send_button,
        ).forEach { fieldId ->
            onView(withId(fieldId)).perform(scrollTo()).check(matches(isDisplayed()))
        }

        onView(withId(R.id.contact_back_control)).perform(click())
        assertDestination(navController, NavGraph.Destinations.FAQ)

        onView(withId(R.id.faq_back_control)).perform(click())
        assertDestination(navController, NavGraph.Destinations.PROFILE)

        onView(withId(R.id.profile_help_row)).perform(click())
        assertDestination(navController, NavGraph.Destinations.FAQ)
    }

    private fun runInstrumented(test: (NavController) -> Unit) {
        lateinit var navController: NavController

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                navController = activity.binding.navHostFragment.findNavController()
                navController.navigate(NavGraph.Destinations.FAQ)
            }

            test(navController)
        }
    }

    private fun assertDestination(navController: NavController, destinationId: Int) {
        assertThat(navController.currentDestination?.id, `is`(destinationId))
    }
}
