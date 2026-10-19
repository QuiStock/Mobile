package com.quistock.quistock.order

import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.app.navigation.NavGraph
import com.quistock.quistock.presentation.activity.MainActivity
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderAndPromotionFragmentTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()

    @Test
    fun orderForm_shouldShowValidationForEachMissingFieldAndConfirmSubmission() =
        runInstrumented(NavGraph.Destinations.ORDER) { navController ->
            onView(withId(R.id.btnSubmitOrder)).perform(scrollTo(), click())
            onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.popup_order_invalid)))

            onView(withId(R.id.etOrderQuantity)).perform(scrollTo(), replaceText("20"), closeSoftKeyboard())
            onView(withId(R.id.btnSubmitOrder)).perform(scrollTo(), click())
            onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.popup_order_invalid)))

            onView(withId(R.id.etOrderDate)).perform(scrollTo(), replaceText("15 ago"), closeSoftKeyboard())
            onView(withId(R.id.btnSubmitOrder)).perform(scrollTo(), click())
            onView(withId(R.id.confirmationMessage)).inRoot(isDialog())
                .check(matches(withText(R.string.popup_order_confirm)))
            onView(withId(R.id.confirmationNo)).inRoot(isDialog()).perform(click())
            assertDestination(navController, NavGraph.Destinations.ORDER)

            onView(withId(R.id.btnSubmitOrder)).perform(scrollTo(), click())
            onView(withId(R.id.confirmationYes)).inRoot(isDialog()).perform(click())
            assertDestination(navController, NavGraph.Destinations.ORDER_SENT)
        }

    @Test
    fun promotionForm_shouldShowValidationForEachMissingField() =
        runInstrumented(NavGraph.Destinations.PROMOTION) { _ ->
            onView(withId(R.id.btnSubmitPromotion)).perform(scrollTo(), click())
            onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.popup_promotion_invalid)))

            onView(withId(R.id.etPromotionDiscount)).perform(scrollTo(), replaceText("15"), closeSoftKeyboard())
            onView(withId(R.id.btnSubmitPromotion)).perform(scrollTo(), click())
            onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.popup_promotion_invalid)))
        }

    @Test
    fun promotionForm_withRequiredFields_shouldConfirmSubmission() =
        runInstrumented(NavGraph.Destinations.PROMOTION) { navController ->
            onView(withId(R.id.etPromotionDiscount)).perform(scrollTo(), replaceText("15"), closeSoftKeyboard())
            onView(withId(R.id.etPromotionDuration)).perform(scrollTo(), replaceText("3"), closeSoftKeyboard())
            onView(withId(R.id.btnSubmitPromotion)).perform(scrollTo(), click())
            onView(withId(R.id.confirmationMessage)).inRoot(isDialog())
                .check(matches(withText(R.string.popup_promotion_confirm)))
            onView(withId(R.id.confirmationNo)).inRoot(isDialog()).perform(click())
            assertDestination(navController, NavGraph.Destinations.PROMOTION)

            onView(withId(R.id.btnSubmitPromotion)).perform(scrollTo(), click())
            onView(withId(R.id.confirmationYes)).inRoot(isDialog()).perform(click())
            assertDestination(navController, NavGraph.Destinations.PROMO_SENT)
        }

    private fun runInstrumented(destinationId: Int, test: (NavController) -> Unit) {
        lateinit var navController: NavController

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                navController = activity.binding.navHostFragment.findNavController()
                navController.navigate(destinationId)
            }

            test(navController)
        }
    }

    private fun assertDestination(navController: NavController, destinationId: Int) {
        assertThat(navController.currentDestination?.id, `is`(destinationId))
    }
}
