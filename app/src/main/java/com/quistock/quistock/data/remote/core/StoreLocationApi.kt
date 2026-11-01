package com.quistock.quistock.data.remote.core

import kotlinx.serialization.json.JsonArray
import retrofit2.Response
import retrofit2.http.GET

interface StoreLocationApi {
    @GET("branches")
    suspend fun branches(): Response<JsonArray>
}
