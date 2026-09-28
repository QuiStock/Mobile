package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.AuthTokens
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.RefreshToken
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.SecretStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginUseCaseTests {
    private val authRepository = mockk<AuthRepository>()
    private val storage = mockk<SecretStorage>(relaxed = true)
    private val useCase = LoginUseCase(authRepository, storage)

    @Test
    fun successfulLoginPersistsRefreshTokenBeforeStoringAccessToken() = runBlocking {
        val tokens = AuthTokens(AccessToken("access"), RefreshToken("refresh"))
        val result = AuthResult.Success(tokens)
        coEvery { authRepository.login("user@example.com", "password") } returns result

        assertEquals(result, useCase("user@example.com", "password"))

        coVerify(ordering = io.mockk.Ordering.SEQUENCE) {
            storage.save(tokens.refreshToken, RefreshToken::class)
            storage.save(tokens.accessToken, AccessToken::class)
        }
    }

    @Test
    fun failedLoginDoesNotStoreTokens() = runBlocking {
        coEvery { authRepository.login("user@example.com", "wrong") } returns LoginError.InvalidCredentials

        assertEquals(LoginError.InvalidCredentials, useCase("user@example.com", "wrong"))

        coVerify(exactly = 0) { storage.save(any<Any>(), any()) }
    }
}
