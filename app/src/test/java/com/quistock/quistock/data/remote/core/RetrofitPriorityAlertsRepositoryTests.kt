package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.port.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class RetrofitPriorityAlertsRepositoryTests {
    private val api = mockk<PriorityAlertsApi>()
    private val repository = RetrofitPriorityAlertsRepository(api, mockk<Logger>(relaxed = true))

    private fun setup(products: String) {
        coEvery { api.branches() } returns json("""[{"id":"store","store_id":17,"name":"Fixture store"}]""")
        coEvery { api.products("Fixture store") } returns json(products)
        coEvery { api.flows(any()) } returns json("[]")
    }

    @Test
    fun `fractional stock is rejected while integral decimal stock remains valid`() = runTest {
        setup("[${product("valid", "5.000")},${product("fractional", "2.5")}]")

        val result = repository.read(17, "store") as AlertProductsResult.Success

        assertEquals(listOf("valid"), result.products.map { it.id })
        assertEquals(5L, result.products.single().stock)
        assertEquals(1, result.invalidCount)
        coVerify(exactly = 0) { api.flows("fractional") }
    }

    @Test
    fun `history is rejected rather than choosing analysis by return order`() = runTest {
        setup("[${product("history")},${product("valid")}]")
        coEvery { api.flows("history") } returns json("[${flow("history")},${flow("history")}]")
        coEvery { api.flows("valid") } returns json("[${flow("valid")}]")

        val result = repository.read(17, "store") as AlertProductsResult.Success

        assertEquals(listOf("valid"), result.products.map { it.id })
        assertEquals(StockFlow.HIGH, result.products.single().flow)
        assertEquals(1, result.invalidCount)
    }

    @Test
    fun `failed analysis query fails section instead of publishing incomplete results`() = runTest {
        setup("[${product("valid")}]")
        coEvery { api.flows("valid") } throws IOException("fixture")

        assertEquals(AlertProductsResult.Failure, repository.read(17, "store"))
    }

    @Test
    fun `wrong store code prevents product queries`() = runTest {
        setup("[]")

        assertEquals(AlertProductsResult.Failure, repository.read(17, "another-store"))
        coVerify(exactly = 0) { api.products(any()) }
    }

    @Test
    fun `duplicate product identity and missing mandatory fields fail if no valid items remain`() = runTest {
        setup("[${product("duplicate")},${product("duplicate")},{"id":"incomplete"}]")

        assertEquals(AlertProductsResult.Failure, repository.read(17, "store"))
        coVerify(exactly = 0) { api.flows(any()) }
    }

    @Test
    fun `empty products is success and cancellation propagates`() = runTest {
        setup("[]")
        assertTrue(repository.read(17, "store") is AlertProductsResult.Success)
        coEvery { api.products(any()) } throws CancellationException()
        var cancelled = false
        try {
            repository.read(17, "store")
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    private fun json(value: String) = Response.success(Json.parseToJsonElement(value).jsonArray)

    private fun product(id: String, stock: String = "5") =
        """{"id":"$id","name":"Fixture product","category":"Category","current_stock":$stock,"branch":"Fixture store"}"""

    private fun flow(productId: String) =
        """{"id":"analysis","product_id":"$productId","flow_type":"HIGH","status":"ANALYZED"}"""
}
