package com.quistock.quistock.domain.usecase

class LoginUseCase(private val session: SessionUseCase) {
    fun retryDelayMillis() = session.retryDelayMillis
    suspend operator fun invoke(email: String, password: String) = session.login(email, password)
}
