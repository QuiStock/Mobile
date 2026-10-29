package com.quistock.quistock.home

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.presentation.activity.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeSummaryRenderingTests {
    @get:Rule val authenticated = AuthenticatedSessionRule()

    @Test fun homeUsesRemoteCountsAndOfflineKeepsOnlyIndicatorsRecoveryAndLogout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.tvNumeroVencimento)).check(matches(withText("4")))
            onView(withId(R.id.tvNumeroRuptura)).check(matches(withText("5")))
            onView(withId(R.id.tvNumeroAcoes)).check(matches(withText("5")))
            scenario.onActivity { authenticated.goOffline() }
            onView(withId(R.id.summaryRetry)).check(matches(withText(R.string.summary_reconnect)))
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.bottomNavigation).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.alertsSection).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.summaryLogout).visibility)
                assertEquals("4", activity.findViewById<TextView>(R.id.tvNumeroVencimento).text.toString())
            }
        }
    }
}
