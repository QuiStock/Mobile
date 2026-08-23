package com.quistock.quistock

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.presentation.activity.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun counterStartsAtZeroAndIncrementsAfterClick() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.txtCounter)).check(matches(withText("0")))
            onView(withId(R.id.btnCounter)).perform(click())
            onView(withId(R.id.txtCounter)).check(matches(withText("1")))
        }
    }
}
