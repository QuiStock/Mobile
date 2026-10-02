package com.quistock.quistock.domain.model

sealed interface AuthResult {
    data class Success(val tokens: AuthTokens) :
        LoginResult,
        AuthRefreshResult
}

sealed interface LoginResult : AuthResult

sealed interface LoginError : LoginResult {
    data object NetworkError : LoginError
    data object InvalidCredentials : LoginError
    data object UserDisabled : LoginError
    data object UnexpectedError : LoginError
}

sealed interface AuthRefreshResult : AuthResult

sealed interface AuthRefreshError : AuthRefreshResult {
    data object NetworkError : AuthRefreshError
    data object Timeout : AuthRefreshError
    data object ServerError : AuthRefreshError
    data object InvalidToken : AuthRefreshError
    data object UnexpectedError : AuthRefreshError
}
