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
import java.math.BigDecimal

class RetrofitPriorityAlertsRepositoryTests {
    private val api = mockk<PriorityAlertsApi>()
    private val repository = RetrofitPriorityAlertsRepository(api, mockk<Logger>(relaxed = true))

    private fun setup(products: String) {
        coEvery { api.branches() } returns json("""[{"id":"store","store_id":17,"name":"Fixture store"}]""")
        coEvery { api.products("Fixture store") } returns json(products)
        coEvery { api.current(17) } returns json("[]")
    }

    @Test
    fun `fractional and integral decimal stocks remain exact`() = runTest {
        setup("[${product("valid", "5.000")},${product("fractional", "2.5")}]")

        val result = repository.read(17, "store") as AlertProductsResult.Success

        assertEquals(listOf("valid", "fractional"), result.products.map { it.id })
        assertEquals(BigDecimal("5"), result.products.first().stock)
        assertEquals(BigDecimal("2.5"), result.products.last().stock)
        assertEquals(0, result.invalidCount)
    }

    @Test
    fun `current analysis comes from server selection and new classifier`() = runTest {
        setup("[${product("valid")}]")
        coEvery { api.current(17) } returns json("[${flow("valid")}]")
        val result = repository.read(17, "store") as AlertProductsResult.Success
        assertEquals("analysis", result.products.single().analysisId)
        assertEquals(StockFlow.HIGH, result.products.single().flow)
    }

    @Test
    fun `catalog returns optional sku and weekly sales without requiring an analysis`() = runTest {
        setup(
            """[{"id":"item-1","sku":"SKU-1","name":"Item","category":"Food",
            "current_stock":3,"sales_7d":14.5,"expiration_days":null,"branch":"Fixture store"}]""",
        )

        val product = (repository.read(17, "store") as AlertProductsResult.Success).products.single()

        assertEquals("SKU-1", product.sku)
        assertEquals(BigDecimal("14.5"), product.sales7d)
        assertEquals(null, product.analysisId)
        assertEquals(null, product.flow)
    }

    @Test
    fun `missing optional catalog values remain absent while invalid weekly sales rows are skipped`() = runTest {
        val negativeSales = product("negative").replace("\"branch\"", "\"sales_7d\":-1,\"branch\"")
        val malformedSales = product("malformed").replace("\"branch\"", "\"sales_7d\":\"unknown\",\"branch\"")
        setup("[${product("missing")},$negativeSales,$malformedSales]")

        val result = repository.read(17, "store") as AlertProductsResult.Success

        assertEquals(listOf("missing"), result.products.map { it.id })
        assertEquals(null, result.products.single().sku)
        assertEquals(null, result.products.single().sales7d)
        assertEquals(2, result.invalidCount)
    }

    @Test
    fun `fraction beyond contract precision is omitted with partial count without rounding`() = runTest {
        setup("[${product("valid", "2.3750")},${product("invalid", "2.3751")}]")

        val result = repository.read(17, "store") as AlertProductsResult.Success

        assertEquals(BigDecimal("2.375"), result.products.single().stock)
        assertEquals(1, result.invalidCount)
        coVerify(exactly = 1) { api.current(17) }
    }

    @Test
    fun `failed analysis query fails section instead of publishing incomplete results`() = runTest {
        setup("[${product("valid")}]")
        coEvery { api.current(17) } throws IOException("fixture")

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
        val incomplete = """{"id":"incomplete"}"""
        setup("[${product("duplicate")},${product("duplicate")},$incomplete]")

        assertEquals(AlertProductsResult.Failure, repository.read(17, "store"))
        coVerify(exactly = 1) { api.current(17) }
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
        """{"id":"$id","name":"Fixture product","category":"Category",
            "current_stock":$stock,"branch":"Fixture store"}"""

    private fun flow(productId: String, id: String = "analysis", date: String = "2026-10-20T10:00:00Z") =
        """{"id":"$id","product_id":"$productId","flow_type":"HIGH","status":"ANALYZED",
            "analysis_date":"$date","store_id":17,"is_current":true,
            "classifier":"SALES_PERCENTILES_V1","risks":["STOCKOUT"]}"""
}
