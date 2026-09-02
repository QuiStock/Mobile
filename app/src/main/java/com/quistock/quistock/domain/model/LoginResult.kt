package com.quistock.quistock.domain.model

sealed interface LoginResult {
    data class Success(val email: String) : LoginResult
}

sealed interface LoginError : LoginResult {
    data object UnexpectedError : LoginError
    data object InvalidCredentials : LoginError
    data object UserDisabled : LoginError
    data object NetworkError : LoginError
}
