package com.quistock.quistock.domain.model

sealed interface LegacyLoginResult {
    data class Success(val user: User) : LegacyLoginResult
}

sealed interface LegacyLoginError : LegacyLoginResult {
    data object UnexpectedError : LegacyLoginError
    data object InvalidCredentials : LegacyLoginError
    data object UserDisabled : LegacyLoginError
    data object NetworkError : LegacyLoginError
}
