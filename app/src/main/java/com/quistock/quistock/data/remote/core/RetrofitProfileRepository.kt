package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.port.ProfileRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

interface ProfileApi {
    @GET("profile")
    suspend fun read(): Response<ProfileResponse>
}

@Serializable
class ProfileResponse(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    @SerialName("profile_photo_url") val photoUrl: String? = null,
    @SerialName("store_id") val storeId: Long? = null,
    @SerialName("store_code") val storeCode: String? = null,
    @SerialName("store_name") val storeName: String? = null,
    @SerialName("region_id") val regionId: Long? = null,
    @SerialName("region_code") val regionCode: String? = null,
    @SerialName("region_name") val regionName: String? = null,
) {
    fun toDomain() = UserProfile(
        id, name, email, role, status, photoUrl, storeId, storeName, storeCode,
        regionId, regionCode, regionName,
    )
}

class RetrofitProfileRepository(private val api: ProfileApi) : ProfileRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(): ProfileResult = try {
        val response = api.read()
        when {
            response.code() == 403 -> {
                response.errorBody()?.close()
                ProfileResult.Forbidden
            }
            response.isSuccessful -> response.body()?.let { ProfileResult.Success(it.toDomain()) }
                ?: ProfileResult.Failure()
            else -> {
                response.errorBody()?.close()
                ProfileResult.Failure(
                    if (response.code() == 429) retryAfterMillis(response.headers()["Retry-After"]) else 0,
                )
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Transport errors can contain credentials or personal data. Never log the throwable.
        ProfileResult.Failure()
    }
}

internal fun retryAfterMillis(value: String?, nowMillis: Long = System.currentTimeMillis()): Long {
    val text = value?.trim() ?: return 0
    text.toLongOrNull()?.let {
        return if (it >= 0) it.coerceAtMost(Long.MAX_VALUE / 1000) * 1000 else 0
    }
    return runCatching {
        (ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis)
            .coerceAtLeast(0)
    }.getOrDefault(0)
}
