package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.port.AuthRepository

class LoginUseCase(private val authRepository: AuthRepository, private val session: SessionUseCase) {
    suspend operator fun invoke(email: String, password: String): LoginResult {
        val result = authRepository.login(email, password)
        return if (result is AuthResult.Success &&
            !session.install(result.tokens)
        ) {
            LoginError.UnexpectedError
        } else {
            result
        }
    }
}
