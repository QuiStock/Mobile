package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.LegacyLoginResult
import com.quistock.quistock.domain.port.AuthenticationPort

class LegacyLoginUseCase(val authenticationPort: AuthenticationPort) {
    suspend operator fun invoke(email: String, password: String): LegacyLoginResult =
        authenticationPort.authenticate(email, password)
}
