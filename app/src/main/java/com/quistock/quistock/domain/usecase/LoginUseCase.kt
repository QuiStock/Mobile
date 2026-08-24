package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.port.AuthenticationPort

class LoginUseCase(
    val authenticationPort: AuthenticationPort,
) {
    operator fun invoke(
        email: String,
        password: String,
    ): Boolean = authenticationPort.authenticate(email, password)
}
