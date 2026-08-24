package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.port.AuthenticationPort
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LoginUseCaseTests {
    private val authenticationPort = mockk<AuthenticationPort>()
    private lateinit var useCase: LoginUseCase

    @Before
    fun setup() {
        useCase = LoginUseCase(authenticationPort)
    }

    @Test
    fun `if auth port login returns true, should return true`() {
        val email = "example@email.com"
        val password = "Abc@123!"
        every {
            authenticationPort.authenticate(email, password)
        } returns true

        val result = useCase(email, password)

        result shouldBe true
        verify(exactly = 1) {
            authenticationPort.authenticate(email, password)
        }
    }

    @Test
    fun `if auth port login returns false, should return false`() {
        val email = "example@email.com"
        val password = "Abc@123!"
        every {
            authenticationPort.authenticate(email, password)
        } returns false

        val result = useCase(email, password)

        result shouldBe false
        verify(exactly = 1) {
            authenticationPort.authenticate(email, password)
        }
    }
}
