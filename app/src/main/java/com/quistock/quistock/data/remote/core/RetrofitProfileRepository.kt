package com.quistock.quistock.data.remote.core

import com.quistock.quistock.data.remote.auth.transportFailureCategory
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import retrofit2.Response
import retrofit2.http.GET
import java.io.IOException
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
        id, name, email, internalRole(), internalStatus(), photoUrl, storeId, storeName, storeCode,
        regionId, regionCode, regionName,
    )

    private fun internalRole(): UserRole = when (role) {
        "EMPLOYEE" -> UserRole.EMPLOYEE
        "MANAGER" -> UserRole.MANAGER
        "REGIONAL_MANAGER" -> UserRole.REGIONAL_MANAGER
        else -> throw SerializationException("Unsupported profile role")
    }

    private fun internalStatus(): UserStatus = when (status) {
        "ACTIVE" -> UserStatus.ACTIVE
        "INACTIVE" -> UserStatus.INACTIVE
        else -> throw SerializationException("Unsupported profile status")
    }
}

class RetrofitProfileRepository(private val api: ProfileApi, private val logger: Logger? = null) : ProfileRepository {
    private val logContext = mapOf("operation" to "core_profile", "stage" to "profile_response")

    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(): ProfileResult {
        logger?.debug("Core profile load started", logContext)
        return try {
            mapResponse(api.read())
        } catch (error: CancellationException) {
            logger?.debug("Core profile request cancelled", logContext)
            throw error
        } catch (error: Exception) {
            logger?.info("Core profile load failed", logContext + ("failure" to profileFailureCategory(error)))
            ProfileResult.Failure()
        }
    }

    private fun mapResponse(response: Response<ProfileResponse>): ProfileResult = when {
        response.code() == HttpURLConnection.HTTP_FORBIDDEN -> {
            response.errorBody()?.close()
            logger?.info(
                "Core profile load failed",
                logContext + mapOf("http_status" to response.code(), "failure" to "forbidden"),
            )
            ProfileResult.Forbidden
        }

        response.isSuccessful -> mapBody(response.body())

        else -> {
            response.errorBody()?.close()
            logger?.info(
                "Core profile load failed",
                logContext + mapOf("http_status" to response.code(), "failure" to "http_error"),
            )
            ProfileResult.Failure(
                if (response.code() == HTTP_TOO_MANY_REQUESTS) {
                    retryAfterMillis(response.headers()["Retry-After"])
                } else {
                    0
                },
            )
        }
    }

    private fun mapBody(body: ProfileResponse?): ProfileResult = if (body == null) {
        logger?.info("Core profile load failed", logContext + ("failure" to "empty_body"))
        ProfileResult.Failure()
    } else {
        ProfileResult.Success(body.toDomain())
    }
}

private fun profileFailureCategory(error: Exception): String = when (error) {
    is SessionException -> "session_unavailable"
    is SerializationException -> "invalid_response"
    is IOException -> transportFailureCategory(error)
    else -> "unexpected"
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
