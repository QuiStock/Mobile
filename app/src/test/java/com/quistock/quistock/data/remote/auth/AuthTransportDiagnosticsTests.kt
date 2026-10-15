package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.app.di.createAuthApi
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.port.Logger
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class AuthTransportDiagnosticsTests {
    @Test fun categorizesTransportFailuresWithoutUsingMessages() {
        val failures = listOf(
            UnknownHostException("sensitive") to "dns",
            ConnectException("sensitive") to "connection",
            SocketTimeoutException("sensitive") to "timeout",
            SSLPeerUnverifiedException("sensitive") to "tls_peer_verification",
            SSLHandshakeException("sensitive") to "tls_handshake",
            SSLException("sensitive") to "tls",
            ProtocolException("sensitive") to "protocol",
            IOException("sensitive") to "io",
        )
        failures.forEach { (error, category) -> assertEquals(category, transportFailureCategory(error)) }
    }

    @Test fun logsSafeTransportContextAndPreservesNetworkResult() = runBlocking {
        val logger = mockk<Logger>(relaxed = true)
        val origin = "https://auth.example.test/".toHttpUrl()
        val custody = AuthCookieCustody(MemoryCookieStore(), origin)
        custody.begin(1)
        val client = OkHttpClient.Builder().addInterceptor {
            throw UnknownHostException("sensitive-url-and-credentials")
        }.build()
        val repository = RetrofitAuthRepository(origin, custody, { createAuthApi(origin, it, client) }, logger = logger)
        assertEquals(LoginError.NetworkError, repository.login("private-email", "private-password", 1))
        verify(exactly = 1) {
            logger.info(
                "Auth HTTP transport failed",
                match { context ->
                    context.keys == setOf("operation", "failure", "elapsed_ms") &&
                        context["operation"] == "login" && context["failure"] == "dns" &&
                        context["elapsed_ms"] is Long
                },
            )
        }
    }
}
