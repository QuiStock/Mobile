package com.quistock.quistock.home

import androidx.lifecycle.MutableLiveData
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.R
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.home.HomeNumbers
import com.quistock.quistock.presentation.home.HomeUiState
import com.quistock.quistock.presentation.home.HomeViewModel
import com.quistock.quistock.presentation.home.HomeWarning
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class HomeFragmentTests {
    private lateinit var viewModel: HomeViewModel
    private lateinit var state: MutableLiveData<HomeUiState>

    @Before
    fun setup() {
        state = MutableLiveData(HomeUiState())
        viewModel = mockk(relaxed = true)
        every { viewModel.uiState } returns state
        GlobalContext.get().declare<HomeViewModel>(instance = viewModel, allowOverride = true)
    }

    @Test
    fun loadingAndFailureNeverShowSampleNumbers() = withHome {
        emit(HomeUiState(loading = true))
        onView(withId(R.id.tvIndicatorsStatus)).check(matches(withText(R.string.home_indicators_loading)))
        onView(withId(R.id.tvNumeroVencimento)).check(matches(withText("")))
        onView(withId(R.id.tvNumeroRuptura)).check(matches(withText("")))
        onView(withId(R.id.tvNumeroAcoes)).check(matches(withText("")))

        emit(HomeUiState(error = true))
        onView(withId(R.id.tvIndicatorsStatus)).check(matches(withText(R.string.home_indicators_error)))
        onView(withId(R.id.btnRetryIndicators)).check(matches(isDisplayed()))
        onView(withId(R.id.btnRetryIndicators)).perform(click())
        verify(exactly = 1) { viewModel.retry() }
    }

    @Test
    fun currentNumbersMapToCardsAndHideRetry() = withHome {
        emit(HomeUiState(numbers = HomeNumbers(0, 3, 7)))
        onView(withId(R.id.tvNumeroVencimento)).check(matches(withText("0")))
        onView(withId(R.id.tvNumeroRuptura)).check(matches(withText("3")))
        onView(withId(R.id.tvNumeroAcoes)).check(matches(withText("7")))
        onView(withId(R.id.btnRetryIndicators)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun cachedNumbersRemainVisibleWhileRetryIsDisabled() = withHome {
        emit(HomeUiState(numbers = HomeNumbers(1, 2, 3), warning = HomeWarning.STALE, dataDate = "22/09/2026"))
        onView(withId(R.id.tvIndicatorsStatus))
            .check(matches(withText(message(R.string.home_indicators_stale_warning, "22/09/2026"))))
        onView(withId(R.id.btnRetryIndicators)).check(matches(isEnabled()))

        emit(HomeUiState(numbers = HomeNumbers(1, 2, 3), loading = true, warning = HomeWarning.STALE))
        onView(withId(R.id.tvNumeroVencimento)).check(matches(withText("1")))
        onView(withId(R.id.btnRetryIndicators)).check(matches(isNotEnabled()))
    }

    @Test
    fun futureCacheShowsDeviceDateWarning() = withHome {
        emit(HomeUiState(numbers = HomeNumbers(4, 5, 6), warning = HomeWarning.FUTURE_DATE, dataDate = "24/09/2026"))
        onView(withId(R.id.tvIndicatorsStatus))
            .check(matches(withText(message(R.string.home_indicators_future_date_warning, "24/09/2026"))))
    }

    @Test
    fun recreatingTheViewRequestsIndicatorsAgain() = withHome { scenario ->
        onView(withId(R.id.containerResumo)).check(matches(isDisplayed()))
        verify(exactly = 1) { viewModel.load() }
        scenario.recreate()
        onView(withId(R.id.containerResumo)).check(matches(isDisplayed()))
        verify(exactly = 2) { viewModel.load() }
    }

    private fun withHome(check: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.binding.navHostFragment.findNavController()
                    .navigate(R.id.action_loginFragment_to_homeFragment)
            }
            check(scenario)
        }
    }

    private fun emit(value: HomeUiState) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { state.value = value }
    }

    private fun message(resourceId: Int, date: String): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, date)
}
