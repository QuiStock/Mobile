package com.quistock.quistock.domain.model

// Deliberately not a data class: default diagnostics must not expose personal data.
class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val photoUrl: String?,
    val storeId: Long?,
    val storeName: String?,
    val storeCode: String? = null,
    val regionId: Long? = null,
    val regionCode: String? = null,
    val regionName: String? = null,
) {
    fun rejection(): ProfileRejection? = when {
        hasInvalidIdentity() -> ProfileRejection.INVALID
        role == "ADMIN" || role == "GERENTE_REGIONAL" -> ProfileRejection.ROLE
        hasInvalidStore() || hasInvalidRegion() -> ProfileRejection.INVALID
        storeId == null || storeName.isNullOrBlank() -> ProfileRejection.NO_STORE
        else -> null
    }

    private fun hasInvalidIdentity(): Boolean =
        id.isBlank() || name.isBlank() || email.isBlank() || status != "ACTIVE" ||
            role !in setOf("ADMIN", "GERENTE", "GERENTE_REGIONAL", "FUNCIONARIO")

    private fun hasInvalidStore(): Boolean = when (storeId) {
        null -> storeName != null || storeCode != null
        else -> storeId <= 0
    }

    private fun hasInvalidRegion(): Boolean = when (regionId) {
        null -> regionCode != null || regionName != null
        else -> regionId <= 0
    }
}

enum class ProfileRejection { INVALID, FORBIDDEN, ROLE, NO_STORE }

sealed interface ProfileResult {
    class Success(val profile: UserProfile) : ProfileResult
    class Failure(val retryAfterMillis: Long = 0) : ProfileResult
    data object Forbidden : ProfileResult
}

sealed interface ProfileState {
    data object Empty : ProfileState
    data object Loading : ProfileState
    class Ready(val generation: Long, val profile: UserProfile) : ProfileState
    class Failure(val retryAfterMillis: Long = 0) : ProfileState
    class Rejected(val reason: ProfileRejection, val role: String? = null) : ProfileState
}
