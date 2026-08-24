package com.quistock.quistock.presentation.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.domain.usecase.LoginUseCase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoginViewModelTests {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val loginUseCase = mockk<LoginUseCase>()
    private lateinit var loginViewModel: LoginViewModel

    @Before
    fun setup() {
        loginViewModel = LoginViewModel(loginUseCase)
    }

    @Test
    fun `when authenticating, if use case returns true, should update state to true`() {
        val email = "example@email.com"
        val password = "Abc@123!"
        every { loginUseCase(email, password) } returns true

        loginViewModel.authenticate(email, password)

        loginViewModel.isAuthenticated.value shouldBe true
        verify(exactly = 1) { loginUseCase(email, password) }
    }

    @Test
    fun `when authenticating, if use case returns false, should update state to false`() {
        val email = "example@email.com"
        val password = "Abc@123!"
        every { loginUseCase(email, password) } returns false

        loginViewModel.authenticate(email, password)

        loginViewModel.isAuthenticated.value shouldBe false
        verify(exactly = 1) { loginUseCase(email, password) }
    }
}
