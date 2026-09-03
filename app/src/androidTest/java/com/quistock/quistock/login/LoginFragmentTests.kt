package com.quistock.quistock.login

import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.R
import com.quistock.quistock.app.navigation.NavGraph
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.login.LoginUiState
import com.quistock.quistock.presentation.login.LoginViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class LoginFragmentTests {
    private lateinit var viewModel: LoginViewModel
    private lateinit var uiState: MutableLiveData<LoginUiState>

    @Before
    fun setup() {
        uiState = MutableLiveData(LoginUiState.Idle)
        viewModel = mockk(relaxed = true)
        every { viewModel.uiState } returns uiState
        GlobalContext.get().declare<LoginViewModel>(
            instance = viewModel,
            allowOverride = true,
        )
    }

    @Test
    fun submit_withBlankForm_shouldNotCallAuthenticate() = runInstrumented {
        onEmailFormField().perform(typeText("    "), closeSoftKeyboard())
        onPasswordFormField().perform(typeText("    "), closeSoftKeyboard())

        onSubmitButtonView().perform(scrollTo(), click())

        verify(exactly = 0) { viewModel.authenticate(any(), any()) }
    }

    @Test
    fun submit_withFormFilledIn_shouldCallAuthenticateWithCorrectCredentials() = runInstrumented {
        val email = "example@email.com"
        val password = "Abcd123!"
        onEmailFormField().perform(typeText(email), closeSoftKeyboard())
        onPasswordFormField().perform(typeText(password), closeSoftKeyboard())

        onSubmitButtonView().perform(scrollTo(), click())

        verify(exactly = 1) { viewModel.authenticate(email, password) }
    }

    @Test
    fun idle_shouldEnableSubmitButtonAndHideSpinner() = runInstrumented {
        onSubmitButtonView().check(matches(isEnabled()))
        onLoadingSpinner().check(matches(not(isDisplayed())))
    }

    @Test
    fun loading_shouldDisableSubmitButtonAndShowSpinnerAndHideErrorMessage() = runInstrumented {
        emitUiState(LoginUiState.Loading)

        onSubmitButtonView().check(matches(isNotEnabled()))
        onLoadingSpinner().check(matches(isDisplayed()))
        onErrorMessageView().check(matches(not(isDisplayed())))
    }

    @Test
    fun authenticated_shouldNavigateToHomePage() = runInstrumented { navController ->
        emitUiState(LoginUiState.Authenticated)

        assertThat(
            navController.currentDestination?.id,
            `is`(NavGraph.Destinations.HOME),
        )
    }

    @Test
    fun networkError_shouldEnableSubmitButtonAndHideSpinnerAndShowNetworkErrorMessage() = runInstrumented {
        emitUiState(LoginUiState.Error(LoginError.NetworkError))

        onSubmitButtonView().check(matches(isEnabled()))
        onLoadingSpinner().check(matches(not(isDisplayed())))
        onErrorMessageView().check(matches(withText(R.string.erro_login_internet)))
        onErrorMessageView().check(matches(isDisplayed()))
    }

    @Test
    fun userDisabledError_shouldEnableSubmitButtonAndHideSpinnerAndShowUserDisabledErrorMessage() = runInstrumented {
        emitUiState(LoginUiState.Error(LoginError.UserDisabled))

        onSubmitButtonView().check(matches(isEnabled()))
        onLoadingSpinner().check(matches(not(isDisplayed())))
        onErrorMessageView().check(matches(withText(R.string.erro_login_usuario_desabilitado)))
        onErrorMessageView().check(matches(isDisplayed()))
    }

    @Test
    fun invalidCredentialsError_shouldEnableSubmitButtonAndHideSpinnerAndShowInvalidCredentialsErrorMessage() =
        runInstrumented {
            emitUiState(LoginUiState.Error(LoginError.InvalidCredentials))

            onSubmitButtonView().check(matches(isEnabled()))
            onLoadingSpinner().check(matches(not(isDisplayed())))
            onErrorMessageView().check(matches(withText(R.string.erro_login_credenciais_invalidas)))
            onErrorMessageView().check(matches(isDisplayed()))
        }

    @Test
    fun unexpectedError_shouldEnableSubmitButtonAndHideSpinnerAndShowUnexpectedErrorMessage() = runInstrumented {
        emitUiState(LoginUiState.Error(LoginError.UnexpectedError))

        onSubmitButtonView().check(matches(isEnabled()))
        onLoadingSpinner().check(matches(not(isDisplayed())))
        onErrorMessageView().check(matches(withText(R.string.erro_login_erro_inesperado)))
        onErrorMessageView().check(matches(isDisplayed()))
    }

    private fun runInstrumented(test: (NavController) -> Unit) {
        lateinit var navController: NavController

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navHost = activity.binding.navHostFragment
                navController = navHost.findNavController()
            }

            test(navController)
        }
    }

    private fun emitUiState(state: LoginUiState) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            uiState.value = state
        }
    }

    private fun onSubmitButtonView(): ViewInteraction = onView(withId(R.id.bt_entrar_login))

    private fun onLoadingSpinner(): ViewInteraction = onView(withId(R.id.loading_login))

    private fun onEmailFormField(): ViewInteraction = onView(withId(R.id.email_login))

    private fun onPasswordFormField(): ViewInteraction = onView(withId(R.id.senha_login))

    private fun onErrorMessageView(): ViewInteraction = onView(withId(R.id.txt_erro_login))
}
