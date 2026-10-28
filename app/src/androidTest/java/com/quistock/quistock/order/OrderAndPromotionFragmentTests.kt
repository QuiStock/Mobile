package com.quistock.quistock.order

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
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.presentation.activity.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderAndPromotionFragmentTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()

    @Test
    fun orderForm_shouldShowValidationForEachMissingField() = runInstrumented(NavGraph.Destinations.ORDER) { _ ->
        onView(withId(R.id.btnSubmitOrder)).perform(scrollTo(), click())
        onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.popup_order_invalid)))

        onView(withId(R.id.etOrderQuantity)).perform(scrollTo(), replaceText("20"), closeSoftKeyboard())
        onView(withId(R.id.btnSubmitOrder)).perform(scrollTo(), click())
        onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.popup_order_invalid)))
    }

    @Test
    fun orderForm_withRequiredFields_shouldConfirmSubmission() =
        runInstrumented(NavGraph.Destinations.ORDER) { scenario ->
            onView(withId(R.id.etOrderQuantity)).perform(scrollTo(), replaceText("20"), closeSoftKeyboard())
            onView(withId(R.id.etOrderDate)).perform(scrollTo(), replaceText("15 ago"), closeSoftKeyboard())
            onView(withId(R.id.btnSubmitOrder)).perform(scrollTo(), click())
            onView(withId(R.id.confirmationMessage)).inRoot(isDialog())
                .check(matches(withText(R.string.popup_order_confirm)))
            onView(withId(R.id.confirmationNo)).inRoot(isDialog()).perform(click())
            awaitDestination(scenario, NavGraph.Destinations.ORDER)

            onView(withId(R.id.btnSubmitOrder)).perform(scrollTo(), click())
            onView(withId(R.id.confirmationYes)).inRoot(isDialog()).perform(click())
            awaitDestination(scenario, NavGraph.Destinations.ORDER_SENT)
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
        runInstrumented(NavGraph.Destinations.PROMOTION) { scenario ->
            onView(withId(R.id.etPromotionDiscount)).perform(scrollTo(), replaceText("15"), closeSoftKeyboard())
            onView(withId(R.id.etPromotionDuration)).perform(scrollTo(), replaceText("3"), closeSoftKeyboard())
            onView(withId(R.id.btnSubmitPromotion)).perform(scrollTo(), click())
            onView(withId(R.id.confirmationMessage)).inRoot(isDialog())
                .check(matches(withText(R.string.popup_promotion_confirm)))
            onView(withId(R.id.confirmationNo)).inRoot(isDialog()).perform(click())
            awaitDestination(scenario, NavGraph.Destinations.PROMOTION)

            onView(withId(R.id.btnSubmitPromotion)).perform(scrollTo(), click())
            onView(withId(R.id.confirmationYes)).inRoot(isDialog()).perform(click())
            awaitDestination(scenario, NavGraph.Destinations.PROMO_SENT)
        }

    private fun runInstrumented(destinationId: Int, test: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitDestination(scenario, NavGraph.Destinations.HOME)
            scenario.onActivity { activity ->
                activity.binding.navHostFragment.findNavController().navigate(destinationId)
            }
            awaitDestination(scenario, destinationId)
            test(scenario)
        }
    }
}
