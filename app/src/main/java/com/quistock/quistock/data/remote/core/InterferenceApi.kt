package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.SessionSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

interface InterferenceApi {
    @GET("actions")
    suspend fun list(
        @Query("store_id") storeId: Long,
        @Query("action_type") type: String?,
        @Tag expected: SessionSnapshot,
    ): Response<JsonArray>

    @POST("actions/generate")
    suspend fun generate(@Body body: JsonObject, @Tag expected: SessionSnapshot): Response<JsonObject>

    @PATCH("actions/{id}/status")
    suspend fun forward(
        @Path("id") id: String,
        @Body body: JsonObject,
        @Tag expected: SessionSnapshot,
    ): Response<JsonObject>
}
