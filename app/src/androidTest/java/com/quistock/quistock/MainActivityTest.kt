package com.quistock.quistock

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.presentation.activity.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun launchingAppShowsLoginScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.email_login)).check(matches(isDisplayed()))
            onView(withId(R.id.senha_login)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun tappingRegisterLinkNavigatesToRegisterScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.bt_ir_cadastro)).perform(click())

            onView(withId(R.id.nome_cadastro)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun tappingLoginLinkReturnsToLoginScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.bt_ir_cadastro)).perform(click())
            onView(withId(R.id.bt_ir_login)).perform(click())

            onView(withId(R.id.email_login)).check(matches(isDisplayed()))
        }
    }
}
