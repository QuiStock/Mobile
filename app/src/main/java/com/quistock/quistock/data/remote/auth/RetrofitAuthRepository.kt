package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.data.remote.auth.dto.LoginRequest
import com.quistock.quistock.domain.model.AuthRefreshError
import com.quistock.quistock.domain.model.AuthRefreshResult
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.LogoutRequest
import com.quistock.quistock.domain.port.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RetrofitAuthRepository(
    private val baseUrl: HttpUrl,
    private val custody: AuthCookieCustody,
    private val createApi: (StagedAuthCookieJar) -> AuthApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val logger: Logger? = null,
) : AuthRepository {
    private val diagnostics = AuthTransportDiagnostics(baseUrl, logger)

    @Volatile private var retryAt = 0L
    override suspend fun begin(generation: Long) = custody.begin(generation)
    override fun hasAccess(generation: Long) = custody.access(generation) != null
    override suspend fun clear(generation: Long) = custody.clear(generation)
    override suspend fun restore(generation: Long) = when (custody.restore(generation)) {
        RestoreCookies.MISSING -> StoredSession.MISSING
        RestoreCookies.EXPIRED -> StoredSession.EXPIRED
        RestoreCookies.AVAILABLE -> StoredSession.AVAILABLE
    }
    override fun retryDelayMillis() = (retryAt - now()).coerceAtLeast(0)

    override fun prepareLogout(generation: Long): LogoutRequest {
        // Capture before local revocation. Never commit logout responses into live custody.
        var captured: StagedAuthCookieJar? = custody.requestJar(generation, true, AuthCookieCustody.LOGOUT_PATH)
        val used = AtomicBoolean(false)
        return LogoutRequest {
            check(used.compareAndSet(false, true)) { "Logout request already consumed" }
            val jar = requireNotNull(captured)
            captured = null
            withContext(Dispatchers.IO) {
                val logContext = mapOf("operation" to "auth_logout")
                val started = diagnostics.requestStarted(logContext)
                val call = createApi(jar).logout()
                call.timeout().timeout(LOGOUT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                try {
                    val response = call.execute()
                    try {
                        logger?.debug("Auth HTTP response received", logContext + ("http_status" to response.code()))
                        val confirmed = response.code() == HttpURLConnection.HTTP_NO_CONTENT
                        if (confirmed && listOf(AuthCookieCustody.ACCESS, AuthCookieCustody.REFRESH).any { name ->
                                jar.received().none { it.name == name && it.expiresAt <= now() }
                            }
                        ) {
                            logger?.info("Logout response missing cookie expiry", mapOf("operation" to "auth_logout"))
                        }
                        confirmed
                    } finally {
                        response.errorBody()?.close()
                    }
                } catch (error: IOException) {
                    diagnostics.transportFailed(error, started, logContext)
                    false
                } finally {
                    // Drops references to the captured refresh when the operation finishes.
                    call.cancel()
                }
            }
        }
    }

    override suspend fun login(email: String, password: String, generation: Long): LoginResult =
        when (request(generation, LoginRequest(email, password))) {
            AuthOutcome.SUCCESS -> AuthResult.Success
            AuthOutcome.INVALID -> LoginError.InvalidCredentials
            AuthOutcome.PLATFORM -> LoginError.PlatformNotAllowed
            AuthOutcome.NETWORK, AuthOutcome.TIMEOUT -> LoginError.NetworkError
            AuthOutcome.LIMIT -> LoginError.RateLimited
            else -> LoginError.UnexpectedError
        }

    override suspend fun refresh(generation: Long): AuthRefreshResult = when (request(generation, null)) {
        AuthOutcome.SUCCESS -> AuthResult.Success
        AuthOutcome.INVALID -> AuthRefreshError.InvalidToken
        AuthOutcome.NETWORK -> AuthRefreshError.NetworkError
        AuthOutcome.TIMEOUT -> AuthRefreshError.Timeout
        AuthOutcome.SERVER -> AuthRefreshError.ServerError
        AuthOutcome.LIMIT -> AuthRefreshError.RateLimited
        AuthOutcome.PROTOCOL -> AuthRefreshError.ProtocolError
        else -> AuthRefreshError.UnexpectedError
    }

    private suspend fun request(generation: Long, login: LoginRequest?): AuthOutcome {
        val limited = retryDelayMillis() > 0
        val jar = custody.requestJar(generation, refresh = login == null)
        val refreshUrl = requireNotNull(baseUrl.resolve(AuthCookieCustody.REFRESH_PATH))
        if (limited || (login == null && !jar.hasRefresh(refreshUrl))) {
            return if (limited) AuthOutcome.LIMIT else AuthOutcome.INVALID
        }
        val api = createApi(jar)
        return withContext(Dispatchers.IO) {
            val context = AuthRequestContext(baseUrl, custody, logger, ::updateRetryAfter)
            AuthRequest(generation, login, jar, api, context).execute()
        }
    }

    private fun updateRetryAfter(response: Response<*>) {
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

    companion object {
        private const val LOGOUT_TIMEOUT_SECONDS = 10L
        private const val MILLIS_PER_SECOND = 1000L
    }
}
