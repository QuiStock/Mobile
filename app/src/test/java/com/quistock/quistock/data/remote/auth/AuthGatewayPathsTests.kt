package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.app.di.createAuthApi
import com.quistock.quistock.data.remote.auth.dto.LoginRequest
import okhttp3.CookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthGatewayPathsTests {
    @Test fun allAuthenticationRequestsKeepTheConfiguredGatewayPrefix() {
        MockWebServer().use { server ->
            server.start()
            val api = createAuthApi(server.url("/auth/"), CookieJar.NO_COOKIES)
            repeat(4) { server.enqueue(MockResponse().setResponseCode(204)) }
            api.login(LoginRequest("synthetic@example.test", "synthetic")).execute()
            api.csrf().execute()
            api.refresh("synthetic-csrf").execute()
            api.logout("synthetic-csrf").execute()
            assertEquals("/auth/login", server.takeRequest().path)
            assertEquals("/auth/csrf", server.takeRequest().path)
            assertEquals("/auth/refresh", server.takeRequest().path)
            assertEquals("/auth/logout", server.takeRequest().path)
        }
    }
}
