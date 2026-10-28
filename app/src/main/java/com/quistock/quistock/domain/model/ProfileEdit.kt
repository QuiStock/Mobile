package com.quistock.quistock.domain.model

/** Optional fields are omitted; an empty photo URL explicitly removes the association. */
class ProfileChanges(val name: String? = null, val photoUrl: String? = null) {
    fun isValid(): Boolean = when {
        name == null && photoUrl == null -> false
        name != null && profileNameError(name) != null -> false
        photoUrl.isNullOrEmpty() -> true
        else -> validPhotoUrl(photoUrl)
    }

    fun matches(profile: UserProfile): Boolean =
        (name == null || name.trim { Character.isWhitespace(it) } == profile.name) &&
            (photoUrl == null || photoUrl.ifEmpty { null } == profile.photoUrl)
}

private fun validPhotoUrl(value: String): Boolean = try {
    val url = java.net.URI(value)
    val publicHttps = url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null
    publicHttps && value.length <= 500
} catch (_: java.net.URISyntaxException) {
    false
}

sealed interface ProfileEditResult {
    class Saved(val profile: UserProfile) : ProfileEditResult
    class Rejected(val invalidInput: Boolean = false, val retryAfterMillis: Long = 0) : ProfileEditResult
    class CheckLater(val retryAfterMillis: Long) : ProfileEditResult
    data object Uncertain : ProfileEditResult
    data object Forbidden : ProfileEditResult
}

enum class ProfileNameError { REQUIRED, TOO_LONG }

fun profileNameError(name: String): ProfileNameError? = when {
    name.isBlank() -> ProfileNameError.REQUIRED
    name.length > 150 -> ProfileNameError.TOO_LONG
    else -> null
}
