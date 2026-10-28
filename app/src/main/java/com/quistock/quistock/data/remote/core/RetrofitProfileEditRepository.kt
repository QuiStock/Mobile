package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.port.ProfileEditRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Tag
import retrofit2.Response

interface ProfileEditApi {
    @PATCH("profile")
    suspend fun update(
        @Body request: ProfileEditRequest,
        @Tag expected: SessionSnapshot,
    ): Response<ProfileResponse>
}

@Serializable
class ProfileEditRequest(
    val name: String? = null,
    @SerialName("profile_photo_url") val photoUrl: String? = null,
)

class RetrofitProfileEditRepository(private val api: ProfileEditApi) : ProfileEditRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun update(changes: ProfileChanges, expected: SessionSnapshot): ProfileEditResult = try {
        map(api.update(ProfileEditRequest(changes.name, changes.photoUrl), expected))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // A missing/incompatible response cannot establish whether the write was committed.
        ProfileEditResult.Uncertain
    }

    private fun map(response: Response<ProfileResponse>): ProfileEditResult {
        response.errorBody()?.close()
        return when (response.code()) {
            200 -> response.body()?.let { ProfileEditResult.Saved(it.toDomain()) }
                ?: ProfileEditResult.Uncertain
            400 -> ProfileEditResult.Rejected(invalidInput = true)
            403 -> ProfileEditResult.Forbidden
            429 -> ProfileEditResult.Rejected(
                retryAfterMillis = retryAfterMillis(response.headers()["Retry-After"]),
            )
            in 400..499 -> ProfileEditResult.Rejected()
            else -> ProfileEditResult.Uncertain
        }
    }
}
