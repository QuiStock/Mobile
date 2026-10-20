package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.port.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import java.net.HttpURLConnection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

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
            response.code() == HttpURLConnection.HTTP_FORBIDDEN -> {
                response.errorBody()?.close()
                ProfileResult.Forbidden
            }

            response.isSuccessful -> response.body()?.let { ProfileResult.Success(it.toDomain()) }
                ?: ProfileResult.Failure()

            else -> {
                response.errorBody()?.close()
                ProfileResult.Failure(
                    if (response.code() ==
                        HTTP_TOO_MANY_REQUESTS
                    ) {
                        retryAfterMillis(response.headers()["Retry-After"])
                    } else {
                        0
                    },
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

private const val HTTP_TOO_MANY_REQUESTS = 429

internal fun retryAfterMillis(value: String?, nowMillis: Long = System.currentTimeMillis()): Long {
    val text = value?.trim().orEmpty()
    val seconds = text.toLongOrNull()
    return if (seconds != null) {
        seconds.coerceIn(0, Long.MAX_VALUE / TimeUnit.SECONDS.toMillis(1)) * TimeUnit.SECONDS.toMillis(1)
    } else {
        runCatching {
            (ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis)
                .coerceAtLeast(0)
        }.getOrDefault(0)
    }
}
