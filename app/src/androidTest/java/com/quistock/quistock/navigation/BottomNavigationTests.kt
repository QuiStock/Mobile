package com.quistock.quistock.navigation

import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.SessionUseCase
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class BottomNavigationTests {
    @get:Rule val session = AuthenticatedSessionRule()
    private var previousSkeleton = true

    @Before fun disableVisualDelay() {
        previousSkeleton = SkeletonLoadingScreen.enabled
        SkeletonLoadingScreen.enabled = false
    }

    @After fun restoreVisualDelay() {
        SkeletonLoadingScreen.enabled = previousSkeleton
    }

    @Test fun operatorTabsNavigateAndBackConsumesHistoryAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            onView(withId(R.id.navigation_employees)).check(doesNotExist())
            onView(withId(R.id.navigation_products)).check(matches(withText(R.string.navigation_products)))
            visit(scenario, R.id.navigation_products, R.id.productControlFragment)
            visit(scenario, R.id.navigation_chatbot, R.id.chatbotFragment)
            scenario.recreate()
            awaitNavigationReady(scenario, R.id.chatbotFragment)
            onView(withId(R.id.navigation_chatbot)).check(matches(isSelected()))
            pressBack()
            awaitNavigationReady(scenario, R.id.productControlFragment)
            pressBack()
            awaitNavigationReady(scenario, R.id.homeFragment)
        }
    }

    @Test fun filtersSurviveTabSwitchAndRecreationAndReselection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            visit(scenario, R.id.navigation_products, R.id.productControlFragment)
            onView(withId(R.id.etBuscaProduto)).perform(replaceText("fixture filter"), closeSoftKeyboard())
            visit(scenario, R.id.navigation_interferences, R.id.interferencesFragment)
            visit(scenario, R.id.navigation_products, R.id.productControlFragment)
            onView(withId(R.id.etBuscaProduto)).check(matches(withText("fixture filter")))
            scenario.recreate()
            awaitNavigationReady(scenario, R.id.productControlFragment)
            onView(withId(R.id.etBuscaProduto)).check(matches(withText("fixture filter")))
            onView(withId(R.id.navigation_products)).perform(click())
            onView(withId(R.id.etBuscaProduto)).check(matches(withText("fixture filter")))
        }
    }

    @Test fun rapidSecondTapCannotStartAnotherTransition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.navigation_products).performClick()
                activity.findViewById<android.view.View>(R.id.navigation_chatbot).performClick()
            }
            awaitNavigationReady(scenario, R.id.productControlFragment)
            onView(withId(R.id.navigation_products)).perform(click())
            pressBack()
            awaitNavigationReady(scenario, R.id.homeFragment)
        }
    }

    @Test fun homeBackAsksBeforeExitAndCancelKeepsNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            pressBack()
            onView(withText(R.string.navigation_exit_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.navigation_exit_cancel)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.navigation_home)).check(matches(isSelected()))
            visit(scenario, R.id.navigation_products, R.id.productControlFragment)
        }
    }

    @Test fun confirmingExitFinishesActivityWithoutLoggingOut() {
        val currentSession = GlobalContext.get().get<SessionUseCase>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            pressBack()
            onView(withText(R.string.navigation_exit_confirm)).inRoot(isDialog()).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            assertEquals(SessionState.Active, currentSession.state.value)
        }
    }

    @Test fun reselectionScrollsProductsToTopWithoutAddingAVisit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            visit(scenario, R.id.navigation_products, R.id.productControlFragment)
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<ScrollView>(R.id.scrollProductControl)
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                assertTrue(scroll.scrollY > 0)
            }
            onView(withId(R.id.navigation_products)).perform(click())
            scenario.onActivity { activity ->
                assertEquals(0, activity.findViewById<ScrollView>(R.id.scrollProductControl).scrollY)
            }
            pressBack()
            awaitNavigationReady(scenario, R.id.homeFragment)
        }
    }

    @Test fun keyboardHidesTheBarAndClosingItRestoresTheBar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            visit(scenario, R.id.navigation_products, R.id.productControlFragment)
            onView(withId(R.id.etBuscaProduto)).perform(click())
            scenario.onActivity { activity ->
                val input = activity.findViewById<android.view.View>(R.id.etBuscaProduto)
                // Samsung Android 12 rejects an InsetsController request sent with DecorView.
                val manager = requireNotNull(activity.getSystemService(InputMethodManager::class.java))
                manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            awaitNavigationReady(scenario, R.id.productControlFragment) {
                val visible = ViewCompat.getRootWindowInsets(it.binding.root)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                visible && !it.binding.mainBottomNavigation.isShown
            }
            onView(withId(R.id.etBuscaProduto)).perform(closeSoftKeyboard())
            awaitNavigationReady(scenario, R.id.productControlFragment) {
                it.binding.mainBottomNavigation.isShown
            }
        }
    }

    private fun visit(scenario: ActivityScenario<MainActivity>, button: Int, destination: Int) {
        onView(withId(button)).perform(click())
        awaitNavigationReady(scenario, destination)
        onView(withId(button)).check(matches(isSelected()))
    }
}
