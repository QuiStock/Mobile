package com.quistock.quistock.presentation.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.LegacyLoginError
import com.quistock.quistock.domain.model.LegacyLoginResult
import com.quistock.quistock.domain.model.User
import com.quistock.quistock.domain.port.UserPreferences
import com.quistock.quistock.domain.usecase.LegacyLoginUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTests {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val loginUseCase = mockk<LegacyLoginUseCase>()
    private val userPreferences = mockk<UserPreferences>(relaxUnitFun = true)
    private lateinit var loginViewModel: LoginViewModel

    @Before
    fun setup() {
        loginViewModel = LoginViewModel(loginUseCase, userPreferences)
    }

    @Test
    fun `initial state should be idle and user email should be null`() = runTest {
        loginViewModel.uiState.value shouldBe LoginUiState.Idle
        loginViewModel.userEmail.value shouldBe null
    }

    @Test
    fun `if auth has not answered yet, state should be loading`() = runTest {
        val authResult = CompletableDeferred<LegacyLoginResult>()
        coEvery {
            loginUseCase(any(), any())
        } coAnswers { authResult.await() }

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()

        loginViewModel.uiState.value shouldBe LoginUiState.Loading
        coVerify(exactly = 1) { loginUseCase(any(), any()) }

        authResult.complete(successfulLoginResult())
        advanceUntilIdle()
    }

    @Test
    fun `if state is loading, login use case should not be called again`() = runTest {
        val authResult = CompletableDeferred<LegacyLoginResult>()
        coEvery {
            loginUseCase(any(), any())
        } coAnswers { authResult.await() }

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()
        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()

        loginViewModel.uiState.value shouldBe LoginUiState.Loading
        coVerify(exactly = 1) { loginUseCase(any(), any()) }

        authResult.complete(successfulLoginResult())
        advanceUntilIdle()
    }

    @Test
    fun `if authentication succeeds, should store user data and become authenticated`() = runTest {
        val email = "example@email.com"
        val userId = "user-123"
        coEvery {
            loginUseCase(any(), any())
        } returns LegacyLoginResult.Success(User(id = userId, email = email))

        loginViewModel.authenticate(email, "Abc@123!")
        advanceUntilIdle()

        loginViewModel.userEmail.value shouldBe email
        loginViewModel.uiState.value shouldBe LoginUiState.Authenticated
        coVerify(exactly = 1) { loginUseCase(email, "Abc@123!") }
        verify(exactly = 1) { userPreferences.saveUserId(userId) }
    }

    @Test
    fun `if credentials are invalid, state should contain invalid credentials error`() = runTest {
        verifyErrorResult(LegacyLoginError.InvalidCredentials)
    }

    @Test
    fun `if user is disabled, state should contain user disabled error`() = runTest {
        verifyErrorResult(LegacyLoginError.UserDisabled)
    }

    @Test
    fun `if network fails, state should contain network error`() = runTest {
        verifyErrorResult(LegacyLoginError.NetworkError)
    }

    @Test
    fun `if use case returns unexpected error, state should preserve it`() = runTest {
        verifyErrorResult(LegacyLoginError.UnexpectedError)
    }

    private suspend fun TestScope.verifyErrorResult(error: LegacyLoginError) {
        coEvery {
            loginUseCase(any(), any())
        } returns error

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        advanceUntilIdle()

        loginViewModel.uiState.value shouldBe LoginUiState.Error(reason = error)
        loginViewModel.userEmail.value shouldBe null
        coVerify(exactly = 1) { loginUseCase(any(), any()) }
        verify(exactly = 0) { userPreferences.saveUserId(any()) }
    }

    private fun successfulLoginResult() = LegacyLoginResult.Success(User(id = "user-123", email = "example@email.com"))
}
