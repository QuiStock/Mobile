package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.port.AuthenticationPort
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
    fun `should return success from authentication port`() = runTest {
        val expected = LoginResult.Success("example@email.com")
        coEvery {
            authenticationPort.authenticate(any(), any())
        } returns expected

        val result = useCase("example@email.com", "Abc@123!")

        result shouldBe expected
        coVerify(exactly = 1) {
            authenticationPort.authenticate("example@email.com", "Abc@123!")
        }
    }

    @Test
    fun `should return error from authentication port`() = runTest {
        val expected = LoginError.InvalidCredentials
        coEvery {
            authenticationPort.authenticate(any(), any())
        } returns expected

        val result = useCase("example@email.com", "wrong-password")

        result shouldBe expected
        coVerify(exactly = 1) {
            authenticationPort.authenticate("example@email.com", "wrong-password")
        }
    }
}
