package com.quistock.quistock.profile

import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isFocusable
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.ProfileEditRepository
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class ProfileEditNavigationTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()
    private lateinit var previous: ProfileEditRepository
    private val writer = mockk<ProfileEditRepository>()

    @Before fun setup() {
        val koin = GlobalContext.get()
        previous = koin.get()
        koin.declare(writer, allowOverride = true)
        runBlocking { koin.get<ProfileUseCase>().load() }
    }

    @After fun cleanup() { GlobalContext.get().declare(previous, allowOverride = true) }

    @Test fun recreationRetainsDraftAndCancelConfirmsDiscard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openEditor(scenario)
            onView(withId(R.id.profile_full_name_input)).perform(replaceText("Updated fixture"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.profile_full_name_input)).check(matches(withText("Updated fixture")))
            onView(withId(R.id.profile_cancel_button)).perform(scrollTo(), click())
            onView(withText(R.string.profile_discard_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.profile_continue_editing)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.profile_full_name_input)).check(matches(withText("Updated fixture")))
            coVerify(exactly = 0) { writer.update(any(), any()) }
        }
    }

    @Test fun heldWriteSurvivesRecreationAndNavigatesOnlyAfterConfirmation() {
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<ProfileEditResult>()
        coEvery { writer.update(any(), any()) } coAnswers {
            entered.complete(Unit)
            response.await()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openEditor(scenario)
            onView(withId(R.id.profile_full_name_input)).perform(replaceText("Updated fixture"), closeSoftKeyboard())
            onView(withId(R.id.profile_save_changes_button)).perform(scrollTo(), click())
            runBlocking { withTimeout(OPERATION_TIMEOUT) { entered.await() } }
            scenario.recreate()
            onView(withId(R.id.profile_save_changes_button)).check(matches(not(isEnabled())))
            coVerify(exactly = 1) { writer.update(any(), any()) }
            response.complete(ProfileEditResult.Saved(UserProfile(
                "1", "Updated fixture", "fixture@example.test", UserRole.EMPLOYEE,
                UserStatus.ACTIVE, null, 1, "Fixture store",
            )))
            awaitDestination(scenario, R.id.profileFragment)
            onView(withId(R.id.profile_edit_button)).check(matches(isDisplayed()))
        }
    }

    private fun openEditor(scenario: ActivityScenario<MainActivity>) {
        awaitDestination(scenario, R.id.homeFragment)
        scenario.onActivity { nav(it).navigate(R.id.profileFragment) }
        onView(withId(R.id.profile_edit_button)).perform(scrollTo(), click())
        onView(withId(R.id.profile_email_input)).check(matches(not(isFocusable())))
    }

    private fun awaitDestination(scenario: ActivityScenario<MainActivity>, destination: Int) {
        lateinit var idler: ProfileDestinationIdler
        scenario.onActivity { idler = ProfileDestinationIdler(nav(it), destination) }
        IdlingRegistry.getInstance().register(idler)
        try {
            onView(isRoot()).check(matches(isDisplayed()))
        } finally {
            IdlingRegistry.getInstance().unregister(idler)
            scenario.onActivity { idler.close() }
        }
    }

    private fun nav(activity: MainActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

    private companion object { const val OPERATION_TIMEOUT = 5_000L }
}
