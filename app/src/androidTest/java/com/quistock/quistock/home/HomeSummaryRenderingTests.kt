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
import com.quistock.quistock.databinding.FragmentHomeBinding
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummary
import com.quistock.quistock.domain.model.SummaryFailure
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.home.HomeSummaryRenderer
import com.quistock.quistock.presentation.home.HomeSummaryUiState
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

    @Test fun renderingKeepsFullIntegersAndShowsFailuresWithoutLosingStaleCounts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val views = FragmentHomeBinding.inflate(activity.layoutInflater)
                val renderer = HomeSummaryRenderer(activity, views)
                val now = System.currentTimeMillis()
                val saved = CachedHomeSummary(
                    HomeSummary(0, Long.MAX_VALUE, 0, 5),
                    now - java.util.concurrent.TimeUnit.DAYS.toMillis(1),
                )
                val expected = java.text.NumberFormat.getIntegerInstance().format(Long.MAX_VALUE)
                for (reason in SummaryFailure.entries) {
                    renderer.render(
                        HomeSummaryUiState(
                            cached = saved,
                            failure = reason,
                            persistenceFailed = true,
                            retryAt = now + 60_000,
                            now = now,
                        ),
                        false,
                    )
                    assertEquals("0", views.tvNumeroVencimento.text.toString())
                    assertEquals(expected, views.tvNumeroRuptura.text.toString())
                    org.junit.Assert.assertTrue(views.tvNumeroRuptura.contentDescription.contains(expected))
                    assertEquals(View.VISIBLE, views.summaryStale.visibility)
                    assertEquals(View.VISIBLE, views.summaryMessage.visibility)
                    assertEquals(View.VISIBLE, views.summaryPersistence.visibility)
                    org.junit.Assert.assertFalse(views.summaryRetry.isEnabled)
                }
            }
        }
    }
}
