package com.quistock.quistock.profile

import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.domain.usecase.EditProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.presentation.activity.MainActivity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class ProfilePhotoOptionsTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()
    private lateinit var previous: EditProfilePhotoUseCase
    private val photos = mockk<EditProfilePhotoUseCase>()

    @Before fun setup() {
        val koin = GlobalContext.get()
        previous = koin.get()
        every { photos.available } returns false
        koin.declare(photos, allowOverride = true)
        runBlocking { koin.get<ProfileUseCase>().load() }
    }

    @After fun cleanup() {
        GlobalContext.get().declare(previous, allowOverride = true)
    }

    @Test fun galleryWithoutConfigurationExplainsUnavailableUpload() = withEditor {
        onView(withText(R.string.profile_choose_gallery)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.profile_upload_unavailable)))
        onView(withId(R.id.profile_full_name_input)).check(matches(isEnabled()))
    }

    @Test fun cameraWithoutConfigurationExplainsUnavailableUpload() = withEditor {
        onView(withText(R.string.profile_take_photo)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.profile_upload_unavailable)))
        onView(withId(R.id.profile_full_name_input)).check(matches(isEnabled()))
    }

    @Test fun optionsAreLabeledAndRemovingAnAbsentPhotoDoesNotCreateChanges() = withEditor {
        onView(withText(R.string.profile_open_settings)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(R.string.profile_remove_photo)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.profile_save_changes_button)).check(matches(not(isEnabled())))
    }

    private fun withEditor(test: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitDestination(scenario, R.id.homeFragment)
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                host.navController.navigate(R.id.editProfileFragment)
            }
            awaitDestination(scenario, R.id.editProfileFragment)
            onView(withId(R.id.profile_change_photo_button)).perform(click())
            test()
        }
    }
}
