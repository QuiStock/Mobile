package com.quistock.quistock.home

import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.presentation.activity.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagerReviewNoticeTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule(UserRole.MANAGER)

    @Test
    fun managerSeesPersistentHomeWarningAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.managerHomeNotice)).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(R.id.managerHomeNotice)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun managerSeesReviewGuidanceInBothRequestDetails() {
        listOf(R.id.managerOrderDetailFragment, R.id.managerPromotionDetailFragment).forEach { destination ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.binding.navHostFragment.findNavController().navigate(destination) }
                onView(withId(R.id.managerReviewNotice)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            }
        }
    }
}
