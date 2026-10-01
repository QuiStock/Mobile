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
class CoreSessionInterceptor(private val origin: HttpUrl, private val session: SessionUseCase) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val url = request.url
        if (url.scheme != origin.scheme || url.host != origin.host || url.port != origin.port) {
            return@runBlocking chain.proceed(request)
        }
        val initial = session.snapshot()
        val recovered = if (initial.accessToken == null ||
            session.state.value is SessionState.Failure
        ) {
            session.refresh(initial)
        } else {
            initial
        }
        if (recovered.generation != initial.generation) throw SessionException()
        val current = session.requireAccess(recovered)
        fun authorized(token: String) = request.newBuilder().header("Authorization", "Bearer $token").build()
        val response = chain.proceed(authorized(requireNotNull(current.accessToken).token))
        if (session.snapshot().generation != current.generation) {
            response.close()
            throw SessionException()
        }
        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return@runBlocking response
        response.close()
        val updated = session.refresh(current)
        if (updated.generation != current.generation) throw SessionException()
        val valid = session.requireAccess(updated)
        val retried = chain.proceed(authorized(requireNotNull(valid.accessToken).token))
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
