package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.port.AuthenticationPort
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
    fun `if auth port login returns true, should return true`() = runTest {
        coEvery {
            authenticationPort.authenticate(any(), any())
        } returns true

        val result = useCase("example@email.com", "Abc@123!")

        result shouldBe true
        coVerify(exactly = 1) {
            authenticationPort.authenticate(any(), any())
        }
    }

    @Test
    fun `if auth port login returns false, should return false`() = runTest {
        coEvery {
            authenticationPort.authenticate(any(), any())
        } returns false

        val result = useCase("example@email.com", "Abc@123!")

        result shouldBe false
        coVerify(exactly = 1) {
            authenticationPort.authenticate(any(), any())
        }
    }
}
