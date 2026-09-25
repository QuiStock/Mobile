package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.LegacyLoginError
import com.quistock.quistock.domain.model.LegacyLoginResult
import com.quistock.quistock.domain.model.User
import com.quistock.quistock.domain.port.AuthenticationPort
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LegacyLoginUseCaseTests {
    private val authenticationPort = mockk<AuthenticationPort>()
    private lateinit var useCase: LegacyLoginUseCase

    @Before
    fun setup() {
        useCase = LegacyLoginUseCase(authenticationPort)
    }

    @Test
    fun `should return success from authentication port`() = runTest {
        val expected = LegacyLoginResult.Success(User(id = "user-123", email = "example@email.com"))
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
        val expected = LegacyLoginError.InvalidCredentials
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
