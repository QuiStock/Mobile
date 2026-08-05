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

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun incrementButtonUpdatesCounter() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.btnCounter)).perform(click())

        onView(withId(R.id.txtCounter)).check(
            matches(
                withText("1"),
            ),
        )
    }
}
