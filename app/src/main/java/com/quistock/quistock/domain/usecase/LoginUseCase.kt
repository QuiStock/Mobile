package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.SecretStorage
import com.quistock.quistock.domain.port.save

class LoginUseCase(private val authRepository: AuthRepository, private val secretStore: SecretStorage) {
    suspend operator fun invoke(email: String, password: String): AuthResult {
        val result = authRepository.login(email = email, password = password)

        if (result is AuthResult.Success) {
            secretStore.save(result.tokens.refreshToken)
            secretStore.save(result.tokens.accessToken)
        }

        return result
    }
}
