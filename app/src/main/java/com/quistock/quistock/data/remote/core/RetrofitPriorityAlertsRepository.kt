package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.Response
import java.math.BigDecimal

class RetrofitPriorityAlertsRepository(
    private val api: PriorityAlertsApi,
    private val logger: Logger,
) : PriorityAlertsRepository {
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
        val products = readAnalyses(candidates)
        val invalidCount = rows.size - products.size
        if (invalidCount > 0) {
            logger.info("Core alert items rejected", context + mapOf("reason" to "invalid_item", "count" to invalidCount))
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

    private suspend fun readAnalyses(products: List<AlertProduct>): List<AlertProduct> = coroutineScope {
        val permits = Semaphore(MAX_CONCURRENT_REQUESTS)
        products.map { product ->
            async {
                permits.withPermit {
                    val flows = body(api.flows(product.id))
                    mapAnalysis(product, flows)
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun mapAnalysis(product: AlertProduct, flows: JsonArray): AlertProduct? {
        if (flows.isEmpty()) return product
        // No client-side selection from history: Core must expose one current analysis.
        val flow = flows.singleOrNull() as? JsonObject
        val type = flow?.text("flow_type")?.let { value -> StockFlow.entries.find { it.name == value } }
        if (flow == null || flow.text("product_id") != product.id || flow.text("status") != "ANALYZED" || type == null) {
            logger.info("Core alert analysis rejected", context + ("reason" to "ambiguous_or_invalid_analysis"))
            return null
        }
        return product.copy(flow = type)
    }

    @Suppress("SwallowedException")
    private fun parseProduct(row: JsonElement, branch: String): AlertProduct? = try {
        val value = row.jsonObject
        require(value.text("branch") == branch)
        val stock = requireNotNull(value.number("current_stock")).longValueExact()
        val expiration = value["expiration_days"]
        val days = if (expiration == null || expiration == JsonNull) null else {
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
        const val MAX_CONCURRENT_REQUESTS = 4
    }
}
