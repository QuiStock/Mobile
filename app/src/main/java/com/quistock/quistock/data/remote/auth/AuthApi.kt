package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.data.remote.auth.dto.CsrfResponse
import com.quistock.quistock.data.remote.auth.dto.LoginRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @GET(AuthCookieCustody.CSRF_PATH)
    fun csrf(): Call<CsrfResponse>

    @POST("/login")
    fun login(@Body request: LoginRequest): Call<Unit>

    @POST(AuthCookieCustody.REFRESH_PATH)
    fun refresh(@Header("X-CSRF-TOKEN") csrfToken: String): Call<Unit>
}
