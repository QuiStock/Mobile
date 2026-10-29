package com.quistock.quistock.domain.model

sealed interface AuthResult {
    data object Success :
        LoginResult,
        AuthRefreshResult
}

sealed interface LoginResult : AuthResult

sealed interface LoginError : LoginResult {
    data object NetworkError : LoginError
    data object InvalidCredentials : LoginError
    data object UserDisabled : LoginError
    data object UnexpectedError : LoginError
    data object PlatformNotAllowed : LoginError
    data object NoActiveStore : LoginError
    data object RateLimited : LoginError
}

sealed interface AuthRefreshResult : AuthResult

sealed interface AuthRefreshError : AuthRefreshResult {
    data object ProtocolError : AuthRefreshError
    data object NetworkError : AuthRefreshError
    data object Timeout : AuthRefreshError
    data object ServerError : AuthRefreshError
    data object InvalidToken : AuthRefreshError
    data object UnexpectedError : AuthRefreshError
    data object RateLimited : AuthRefreshError
}
