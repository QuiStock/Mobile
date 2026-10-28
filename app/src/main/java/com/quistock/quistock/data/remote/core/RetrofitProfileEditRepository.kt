package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.port.ProfileEditRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Tag
import java.net.HttpURLConnection

interface ProfileEditApi {
    @PATCH("profile")
    suspend fun update(@Body request: ProfileEditRequest, @Tag expected: SessionSnapshot): Response<ProfileResponse>
}

@Serializable
class ProfileEditRequest(val name: String? = null, @SerialName("profile_photo_url") val photoUrl: String? = null)

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
            HttpURLConnection.HTTP_OK -> response.body()?.let { ProfileEditResult.Saved(it.toDomain()) }
                ?: ProfileEditResult.Uncertain

            HttpURLConnection.HTTP_BAD_REQUEST -> ProfileEditResult.Rejected(invalidInput = true)

            HttpURLConnection.HTTP_FORBIDDEN -> ProfileEditResult.Forbidden

            HTTP_TOO_MANY_REQUESTS -> ProfileEditResult.Rejected(
                retryAfterMillis = retryAfterMillis(response.headers()["Retry-After"]),
            )

            in HttpURLConnection.HTTP_BAD_REQUEST until HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                ProfileEditResult.Rejected()
            }

            else -> ProfileEditResult.Uncertain
        }
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
