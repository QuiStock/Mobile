package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.data.remote.auth.dto.LoginRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/login")
    fun login(@Body request: LoginRequest): Call<Unit>

    @POST(AuthCookieCustody.REFRESH_PATH)
    fun refresh(): Call<Unit>
}
