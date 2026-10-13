package com.quistock.quistock.popup

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.R
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.common.PopupFeedback
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopupFeedbackTests {
    @Test
    fun managerApprovalDialogs_shouldShowSpecificCopyAndRespectBothActions() {
        var confirmed = false

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                PopupFeedback.showManagerOrderApproval(activity) { confirmed = true }
            }

            onView(withId(R.id.confirmationMessage)).check(matches(withText(R.string.popup_manager_approve_order)))
            onView(withId(R.id.confirmationNo)).perform(click())
            assertThat(confirmed, `is`(false))

            scenario.onActivity { activity ->
                PopupFeedback.showManagerPromotionApproval(activity) { confirmed = true }
            }

            onView(withId(R.id.confirmationMessage))
                .check(matches(withText(R.string.popup_manager_approve_promotion)))
            onView(withId(R.id.confirmationYes)).perform(click())
            assertThat(confirmed, `is`(true))
        }
    }
}
