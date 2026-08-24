package com.quistock.quistock.presentation.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.usecase.LoginUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `initial state should be idle`() = runTest {
        loginViewModel.uiState.value shouldBe LoginUiState.Idle
    }

    @Test
    fun `if auth has not answered yet, state should be loading`() = runTest {
        val authResult = CompletableDeferred<Boolean>()
        coEvery {
            loginUseCase(any(), any())
        } coAnswers { authResult.await() }

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()

        loginViewModel.uiState.value shouldBe LoginUiState.Loading
        coVerify(exactly = 1) { loginUseCase(any(), any()) }

        authResult.complete(true)
        advanceUntilIdle()
    }

    @Test
    fun `if state is loading, login use case should not be called`() = runTest {
        val authResult = CompletableDeferred<Boolean>()
        coEvery {
            loginUseCase(any(), any())
        } coAnswers { authResult.await() }

        // 1st call -> updates state to Loading
        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()

        // 2nd call -> should return early
        loginViewModel.authenticate("example@email.com", "Abc@123!")
        runCurrent()

        loginViewModel.uiState.value shouldBe LoginUiState.Loading
        coVerify(exactly = 1) { loginUseCase(any(), any()) }

        authResult.complete(true)
        advanceUntilIdle()
    }

    @Test
    fun `if authentication succeeds, state should be authenticated`() = runTest {
        coEvery {
            loginUseCase(any(), any())
        } returns true

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        advanceUntilIdle()

        loginViewModel.uiState.value shouldBe LoginUiState.Authenticated
        coVerify(exactly = 1) { loginUseCase(any(), any()) }
    }

    @Test
    fun `if authentication fails, state should be invalid credentials`() = runTest {
        coEvery {
            loginUseCase(any(), any())
        } returns false

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        advanceUntilIdle()

        loginViewModel.uiState.value shouldBe LoginUiState.Error(reason = LoginError.InvalidCredentials)
        coVerify(exactly = 1) { loginUseCase(any(), any()) }
    }

    @Test
    fun `if an exception is thrown, state should be unexpected error`() = runTest {
        coEvery {
            loginUseCase(any(), any())
        } throws Exception("Unexpected exception")

        loginViewModel.authenticate("example@email.com", "Abc@123!")
        advanceUntilIdle()

        loginViewModel.uiState.value shouldBe LoginUiState.Error(reason = LoginError.Unexpected)
        coVerify(exactly = 1) { loginUseCase(any(), any()) }
    }
}
