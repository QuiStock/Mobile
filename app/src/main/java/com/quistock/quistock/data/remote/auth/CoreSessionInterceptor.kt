package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpURLConnection

/** Origin must be explicitly configured; legacy internal integrations are not implicitly Core. */
class CoreSessionInterceptor(
    private val origin: HttpUrl,
    private val session: SessionUseCase,
    private val custody: AuthCookieCustody,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val url = request.url
        if (url.scheme != origin.scheme || url.host != origin.host || url.port != origin.port) {
            return@runBlocking chain.proceed(request)
        }
        // A failed renewal waits for explicit recovery; background calls cannot retry it.
        val failure = session.state.value as? SessionState.Failure
        if (failure != null) throw SessionException(failure.reason)
        val initial = session.snapshot()
        val recovered = if (!initial.hasAccess ||
            session.state.value is SessionState.Failure
        ) {
            session.refresh(initial)
        } else {
            initial
        }
        if (recovered.generation != initial.generation) throw SessionException()
        val current = session.requireAccess(recovered)
        fun authorized(token: String) =
            request.newBuilder().removeHeader("Cookie").header("Authorization", "Bearer $token").build()
        val response = chain.proceed(authorized(custody.access(current.generation) ?: throw SessionException()))
        if (session.snapshot().generation != current.generation) {
            response.close()
            throw SessionException()
        }
        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return@runBlocking response
        response.close()
        val updated = session.refresh(current)
        if (updated.generation != current.generation) throw SessionException()
        val valid = session.requireAccess(updated)
        val retried = chain.proceed(authorized(custody.access(valid.generation) ?: throw SessionException()))
        if (session.snapshot().generation != valid.generation) {
            retried.close()
            throw SessionException()
        }
        if (retried.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            retried.close()
            session.expire(valid)
            throw SessionException()
        }
        retried
    }
}
