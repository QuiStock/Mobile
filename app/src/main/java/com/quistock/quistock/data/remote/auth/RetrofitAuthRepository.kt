package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.StoredSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class LoginRequest(val email: String, val password: String, val platform: String = "mobile") {
    override fun toString() = "LoginRequest(redacted)"
}

interface AuthApi {
    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<Unit>

    @POST("auth/refresh")
    fun refresh(): Call<Unit>
}

class RetrofitAuthRepository(
    private val baseUrl: HttpUrl,
    private val custody: AuthCookieCustody,
    private val client: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build(),
    private val now: () -> Long = System::currentTimeMillis,
) : AuthRepository {
    @Volatile private var retryAt = 0L
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    override suspend fun begin(generation: Long) = custody.begin(generation)
    override fun hasAccess(generation: Long) = custody.access(generation) != null
    override suspend fun clear(generation: Long) = custody.clear(generation)
    override suspend fun restore(generation: Long) = when (custody.restore(generation)) {
        RestoreCookies.MISSING -> StoredSession.MISSING
        RestoreCookies.EXPIRED -> StoredSession.EXPIRED
        RestoreCookies.AVAILABLE -> StoredSession.AVAILABLE
    }
    override fun retryDelayMillis() = (retryAt - now()).coerceAtLeast(0)

    override suspend fun login(email: String, password: String, generation: Long): LoginResult =
        when (request(generation, LoginRequest(email, password))) {
            Outcome.SUCCESS -> AuthResult.Success
            Outcome.INVALID -> LoginError.InvalidCredentials
            Outcome.PLATFORM -> LoginError.PlatformNotAllowed
            Outcome.NETWORK, Outcome.TIMEOUT -> LoginError.NetworkError
            Outcome.LIMIT -> LoginError.RateLimited
            else -> LoginError.UnexpectedError
        }

    override suspend fun refresh(generation: Long): AuthRefreshResult = when (request(generation, null)) {
        Outcome.SUCCESS -> AuthResult.Success
        Outcome.INVALID -> AuthRefreshError.InvalidToken
        Outcome.NETWORK -> AuthRefreshError.NetworkError
        Outcome.TIMEOUT -> AuthRefreshError.Timeout
        Outcome.SERVER -> AuthRefreshError.ServerError
        Outcome.LIMIT -> AuthRefreshError.RateLimited
        else -> AuthRefreshError.UnexpectedError
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun request(generation: Long, login: LoginRequest?): Outcome {
        val limited = retryDelayMillis() > 0
        val jar = custody.requestJar(generation, refresh = login == null)
        val refreshUrl = requireNotNull(baseUrl.resolve("auth/refresh"))
        if (limited || (login == null && jar.loadForRequest(refreshUrl).isEmpty())) {
            return if (limited) Outcome.LIMIT else Outcome.INVALID
        }
        val api = Retrofit.Builder().baseUrl(baseUrl)
            .client(
                client.newBuilder().cookieJar(jar).retryOnConnectionFailure(false)
                    .followRedirects(false).followSslRedirects(false).build(),
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(AuthApi::class.java)
        return withContext(Dispatchers.IO) {
            val response = try {
                if (login == null) api.refresh().execute() else api.login(login).execute()
            } catch (_: SocketTimeoutException) {
                return@withContext Outcome.TIMEOUT
            } catch (_: IOException) {
                return@withContext Outcome.NETWORK
            }
            // Stay in the worker until cookies are durably committed. Cancellation at the dispatcher
            // boundary must not lose a successor after the server has consumed the old refresh.
            try {
                val outcome = classify(response, login != null)
                if (!custody.commit(generation, jar.received(), outcome == Outcome.SUCCESS)) {
                    Outcome.UNEXPECTED
                } else {
                    if (outcome == Outcome.LIMIT) updateRetryAfter(response)
                    outcome
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Outcome.UNEXPECTED
            } finally {
                response.errorBody()?.close()
            }
        }
    }

    private fun updateRetryAfter(response: Response<Unit>) {
        val value = response.headers()["Retry-After"]
        val seconds = value?.toLongOrNull()?.takeIf { it >= 0 }
        val currentTime = now()
        retryAt = if (seconds != null) {
            currentTime + seconds.coerceAtMost((Long.MAX_VALUE - currentTime) / MILLIS_PER_SECOND) * MILLIS_PER_SECOND
        } else {
            runCatching {
                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }.getOrDefault(currentTime)
        }
    }

    private fun classify(response: Response<Unit>, login: Boolean): Outcome {
        val code = runCatching {
            json.parseToJsonElement(response.errorBody()?.string().orEmpty()).jsonObject["code"]?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            response.code() == HttpURLConnection.HTTP_OK -> Outcome.SUCCESS

            response.code() == HTTP_TOO_MANY_REQUESTS -> Outcome.LIMIT

            response.code() >= HttpURLConnection.HTTP_INTERNAL_ERROR -> Outcome.SERVER

            response.code() == HttpURLConnection.HTTP_UNAUTHORIZED &&
                code == (if (login) "invalid_credentials" else "invalid_refresh_token") ->
                Outcome.INVALID

            login && response.code() == HttpURLConnection.HTTP_FORBIDDEN && code == "platform_not_allowed" ->
                Outcome.PLATFORM

            else -> Outcome.UNEXPECTED
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }

    private enum class Outcome { SUCCESS, INVALID, PLATFORM, NETWORK, TIMEOUT, SERVER, LIMIT, UNEXPECTED }
}
