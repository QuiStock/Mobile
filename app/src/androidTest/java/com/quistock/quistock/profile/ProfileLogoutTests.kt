package com.quistock.quistock.profile

import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.LogoutRequest
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.profile.LogoutProgressDialog
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileLogoutTests {
    private val auth = mockk<AuthRepository>(relaxed = true)
    @get:Rule val authenticatedSession = AuthenticatedSessionRule(auth = auth)
    private val remote = CompletableDeferred<Boolean>()

    @Before fun setup() {
        every { auth.prepareLogout(any()) } returns LogoutRequest { remote.await() }
    }

    @Test fun cancellingConfirmationPreservesSessionWithoutCallingLogout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProfile(scenario)
            onView(withId(R.id.profile_sign_out_button)).perform(scrollTo(), click())
            onView(withText(R.string.logout_confirmation_message)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            scenario.onActivity { assertEquals(R.id.profileFragment, nav(it).currentDestination?.id) }
            coVerify(exactly = 0) { auth.clear(any()) }
            io.mockk.verify(exactly = 0) { auth.prepareLogout(any()) }
        }
    }

    @Test fun headerPhotoOpensProfileAndBackReturnsToExistingHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProfile(scenario)
            scenario.onActivity { assertEquals(R.id.profileFragment, nav(it).currentDestination?.id) }
            onView(withId(R.id.profile_back_control)).perform(click())
            awaitDestination(scenario, R.id.homeFragment)
            scenario.onActivity { assertEquals(R.id.homeFragment, nav(it).currentDestination?.id) }
        }
    }

    @Test fun recreationKeepsOneLogoutAndFailureWarningAppearsOverClearedLoginStack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProfile(scenario)
            onView(withId(R.id.profile_sign_out_button)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.logout_progress)).inRoot(isDialog()).check(matches(isDisplayed()))
            scenario.recreate()
            scenario.onActivity {
                val dialogs = it.supportFragmentManager.fragments.filterIsInstance<LogoutProgressDialog>()
                assertEquals(1, dialogs.size)
                assertTrue(dialogs.single().requireDialog().isShowing)
            }
            onView(withText(R.string.logout_progress)).inRoot(isDialog()).check(matches(isDisplayed()))
            scenario.onActivity { remote.complete(false) }
            awaitDestination(scenario, R.id.loginFragment)
            onView(withText(R.string.logout_remote_warning_message)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.logout_understood)).inRoot(isDialog()).perform(click())
            scenario.onActivity {
                assertEquals(R.id.loginFragment, nav(it).currentDestination?.id)
                assertFalse(nav(it).popBackStack())
            }
            io.mockk.verify(exactly = 1) { auth.prepareLogout(any()) }
            coVerify(exactly = 1) { auth.clear(any()) }
        }
    }

    private fun openProfile(scenario: ActivityScenario<MainActivity>) {
        awaitDestination(scenario, R.id.homeFragment)
        onView(withId(R.id.btnPerfil)).perform(click())
        awaitDestination(scenario, R.id.profileFragment)
    }

    private fun nav(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
}
