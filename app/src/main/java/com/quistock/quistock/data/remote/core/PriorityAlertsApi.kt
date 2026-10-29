package com.quistock.quistock.data.remote.core

import kotlinx.serialization.json.JsonArray
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface PriorityAlertsApi {
    @GET("branches")
    suspend fun branches(): Response<JsonArray>

    @GET("products")
    suspend fun products(@Query("branch") branch: String): Response<JsonArray>

    @GET("flows/current")
    suspend fun current(@Query("store_id") storeId: Long): Response<JsonArray>

    @GET("flows")
    suspend fun flows(@Query("product_id") productId: String): Response<JsonArray>
}
