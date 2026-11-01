package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.StoreLocation
import com.quistock.quistock.domain.model.StoreLocationResult
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.StoreLocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal

class RetrofitStoreLocationRepository(private val api: StoreLocationApi, private val logger: Logger) :
    StoreLocationRepository {
    private val context = mapOf("operation" to "core_store_location")

    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(storeId: Long): StoreLocationResult = withContext(Dispatchers.IO) {
        try {
            val response = api.branches()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                logger.info("Core store location HTTP failure", context + ("http_status" to response.code()))
                return@withContext StoreLocationResult.Failure
            }
            val branch = requireNotNull(response.body()).map { it.jsonObject }
                .single { it.number("store_id")?.toLongExactOrNull() == storeId }
            toResult(branch)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            logger.info("Core store location unavailable", context + ("reason" to "request_or_contract_failure"))
            StoreLocationResult.Failure
        }
    }

    private fun toResult(branch: JsonObject): StoreLocationResult {
        val (latitude, longitude) = coordinates(branch)
        val location = StoreLocation(
            name = requireNotNull(branch.text("name")),
            street = branch.text("address"),
            city = branch.text("city"),
            state = branch.text("state"),
            latitude = latitude,
            longitude = longitude,
        )
        return if (location.hasAddress || location.hasCoordinates) {
            StoreLocationResult.Success(location)
        } else {
            StoreLocationResult.NoAddress
        }
    }

    /** Both values must be present and in range; otherwise the pair is discarded and the address is used. */
    private fun coordinates(branch: JsonObject): Pair<Double?, Double?> {
        val latitude = branch.number("latitude")?.toDouble()
        val longitude = branch.number("longitude")?.toDouble()
        val valid = latitude != null && longitude != null &&
            latitude in MIN_LATITUDE..MAX_LATITUDE && longitude in MIN_LONGITUDE..MAX_LONGITUDE
        return if (valid) latitude to longitude else null to null
    }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.number(key: String): BigDecimal? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.contentOrNull?.toBigDecimalOrNull()

    private fun BigDecimal.toLongExactOrNull(): Long? = try {
        longValueExact()
    } catch (_: ArithmeticException) {
        null
    }

    private companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}
