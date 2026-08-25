package com.quistock.quistock.presentation.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.usecase.LoginUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

    private val loginUseCase = mockk<LoginUseCase>()
    private lateinit var loginViewModel: LoginViewModel

    @Before
    fun setup() {
        loginViewModel = LoginViewModel(loginUseCase)
    }

    @Test
    fun `initial state should be idle and user email should be null`() = runTest {
        loginViewModel.uiState.value shouldBe LoginUiState.Idle
        loginViewModel.userEmail.value shouldBe null
    }

    @Test
    fun `if auth has not answered yet, state should be loading`() = runTest {
        val authResult = CompletableDeferred<LoginResult>()
        coEvery {
            loginUseCase(any(), any())
        } coAnswers { authResult.await() }

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()

        loginViewModel.uiState.value shouldBe LoginUiState.Loading
        coVerify(exactly = 1) { loginUseCase(any(), any()) }

        authResult.complete(LoginResult.Success("example@email.com"))
        advanceUntilIdle()
    }

    @Test
    fun `if state is loading, login use case should not be called again`() = runTest {
        val authResult = CompletableDeferred<LoginResult>()
        coEvery {
            loginUseCase(any(), any())
        } coAnswers { authResult.await() }

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()
        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()

        loginViewModel.uiState.value shouldBe LoginUiState.Loading
        coVerify(exactly = 1) { loginUseCase(any(), any()) }

        authResult.complete(LoginResult.Success("example@email.com"))
        advanceUntilIdle()
    }

    @Test
    fun `if authentication succeeds, should store email and become authenticated`() = runTest {
        val email = "example@email.com"
        coEvery {
            loginUseCase(any(), any())
        } returns LoginResult.Success(email)

        loginViewModel.authenticate(email, "Abc@123!")
        advanceUntilIdle()

        loginViewModel.userEmail.value shouldBe email
        loginViewModel.uiState.value shouldBe LoginUiState.Authenticated
        coVerify(exactly = 1) { loginUseCase(email, "Abc@123!") }
    }

    @Test
    fun `if credentials are invalid, state should contain invalid credentials error`() = runTest {
        verifyErrorResult(LoginError.InvalidCredentials)
    }

    @Test
    fun `if user is disabled, state should contain user disabled error`() = runTest {
        verifyErrorResult(LoginError.UserDisabled)
    }

    @Test
    fun `if network fails, state should contain network error`() = runTest {
        verifyErrorResult(LoginError.NetworkError)
    }

    @Test
    fun `if use case returns unexpected error, state should preserve it`() = runTest {
        val error = LoginError.UnexpectedError(Exception("Unexpected exception"))

        verifyErrorResult(error)
    }

    @Test
    fun `if use case throws an exception, state should wrap it as unexpected error`() = runTest {
        val exception = Exception("Unexpected exception")
        coEvery {
            loginUseCase(any(), any())
        } throws exception

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        advanceUntilIdle()

        loginViewModel.uiState.value shouldBe LoginUiState.Error(
            reason = LoginError.UnexpectedError(exception),
        )
        coVerify(exactly = 1) { loginUseCase(any(), any()) }
    }

    private suspend fun TestScope.verifyErrorResult(error: LoginError) {
        coEvery {
            loginUseCase(any(), any())
        } returns error

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        advanceUntilIdle()

        loginViewModel.uiState.value shouldBe LoginUiState.Error(reason = error)
        loginViewModel.userEmail.value shouldBe null
        coVerify(exactly = 1) { loginUseCase(any(), any()) }
    }
}
