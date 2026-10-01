package com.quistock.quistock.domain.model

sealed interface SessionState {
    data object Restoring : SessionState
    data object LocalFailure : SessionState
    data object SignedOut : SessionState
    data object Active : SessionState
    data object Expired : SessionState
    data class Failure(val reason: SessionFailure) : SessionState
}
enum class SessionFailure { NETWORK, TIMEOUT, SERVER, UNEXPECTED }
data class SessionSnapshot(val generation: Long, val accessToken: AccessToken?, val revision: Long = 0)
class SessionException(val reason: SessionFailure? = null) : java.io.IOException("Authentication unavailable")
