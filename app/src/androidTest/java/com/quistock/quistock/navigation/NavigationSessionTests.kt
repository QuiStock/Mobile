package com.quistock.quistock.navigation

import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.ProfileTestDependencies
import com.quistock.quistock.R
import com.quistock.quistock.domain.usecase.SessionUseCase
import com.quistock.quistock.presentation.activity.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class NavigationSessionTests {
    @get:Rule val authenticated = AuthenticatedSessionRule()

    @Test fun pendingProfileKeepsBottomBarHiddenUntilItIsReleased() {
        val session = GlobalContext.get().get<SessionUseCase>()
        val release = CompletableDeferred<Unit>()
        val profile = ProfileTestDependencies(session)
        profile.holdReadsUntil(release)
        profile.install()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.main_bottom_navigation)).check(matches(withEffectiveVisibility(GONE)))
                release.complete(Unit)
                awaitNavigationReady(scenario, R.id.homeFragment)
                onView(withId(R.id.navigation_products)).perform(click())
                awaitNavigationReady(scenario, R.id.productControlFragment)
            }
        } finally {
            release.complete(Unit)
            profile.restore()
        }
    }

    @Test fun expiredSessionRejectsAListenerRetainedFromProtectedContent() {
        val session = GlobalContext.get().get<SessionUseCase>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            lateinit var oldButton: View
            scenario.onActivity { activity ->
                oldButton = activity.findViewById(R.id.navigation_products)
                activity.lifecycleScope.launch { session.expire(session.snapshot()) }
            }
            awaitNavigationReady(scenario, R.id.loginFragment)
            scenario.onActivity { oldButton.performClick() }
            awaitNavigationReady(scenario, R.id.loginFragment)
            onView(withId(R.id.main_bottom_navigation)).check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test fun notificationsAreReachedByHeaderAndHaveNoBottomBar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            onView(withId(R.id.btnNotificacoes)).perform(click())
            awaitNavigationReady(scenario, R.id.notificationsFragment)
            onView(withId(R.id.main_bottom_navigation)).check(matches(withEffectiveVisibility(GONE)))
        }
    }
}
