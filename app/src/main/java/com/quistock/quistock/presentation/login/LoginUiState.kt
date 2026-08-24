package com.quistock.quistock.presentation.login

enum class LoginError { InvalidCredentials, Unexpected }

sealed interface LoginUiState {
    data object Idle : LoginUiState

    data object Loading : LoginUiState

    data object Authenticated : LoginUiState

    data class Error(val reason: LoginError) : LoginUiState
}
