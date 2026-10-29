package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.Response
import java.math.BigDecimal

class RetrofitPriorityAlertsRepository(private val api: PriorityAlertsApi, private val logger: Logger) :
    PriorityAlertsRepository {
    private val context = mapOf("operation" to "core_priority_alerts")

    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(storeId: Long, storeCode: String?): AlertProductsResult = withContext(Dispatchers.IO) {
        readProducts(storeId, storeCode)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun readProducts(storeId: Long, storeCode: String?): AlertProductsResult = try {
        val branch = resolveBranch(storeId, storeCode)
        val rows = body(api.products(branch))
        val parsed = rows.map { row -> parseProduct(row, branch) }
        val duplicates = parsed.filterNotNull().groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val candidates = parsed.filterNotNull().filterNot { it.id in duplicates }
        val current = body(api.current(storeId))
        val products = candidates.map { CurrentAnalysisMapper.select(it, current, storeId) }
        val invalidCount = rows.size - products.size
        if (invalidCount > 0) {
            logger.info(
                "Core alert items rejected",
                context + mapOf("reason" to "invalid_item", "count" to invalidCount),
            )
        }
        if (rows.isNotEmpty() && products.isEmpty()) {
            AlertProductsResult.Failure
        } else {
            AlertProductsResult.Success(products, invalidCount)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        logger.info("Core alerts unavailable", context + ("reason" to "request_or_contract_failure"))
        AlertProductsResult.Failure
    }

    private suspend fun resolveBranch(storeId: Long, storeCode: String?): String {
        val branches = body(api.branches()).map { it.jsonObject }
        val branch = branches.single { it.number("store_id")?.longValueExact() == storeId }
        require(storeCode == null || branch.text("id") == storeCode)
        val name = requireNotNull(branch.text("name"))
        require(branches.count { it.text("name") == name } == 1)
        return name
    }

    @Suppress("SwallowedException")
    private fun parseProduct(row: JsonElement, branch: String): AlertProduct? = try {
        val value = row.jsonObject
        require(value.text("branch") == branch)
        val stock = requireNotNull(value.number("current_stock")).stripTrailingZeros()
        require(stock.signum() >= 0 && stock.scale() <= STOCK_DECIMAL_PLACES)
        val expiration = value["expiration_days"]
        val days = if (expiration == null || expiration == JsonNull) {
            null
        } else {
            requireNotNull(value.number("expiration_days")).intValueExact()
        }
        AlertProduct(
            id = requireNotNull(value.text("id")),
            name = requireNotNull(value.text("name")),
            category = requireNotNull(value.text("category")),
            stock = stock,
            expirationDays = days,
            flow = null,
        )
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    private fun body(response: Response<JsonArray>): JsonArray {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            logger.info("Core alerts HTTP failure", context + ("http_status" to response.code()))
            error("Core alerts request failed")
        }
        return requireNotNull(response.body())
    }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): BigDecimal? =
        (get(key) as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull()

    private companion object {
        const val STOCK_DECIMAL_PLACES = 3
    }
}
