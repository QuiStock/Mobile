package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.StoreLocationResult
import com.quistock.quistock.domain.port.Logger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class RetrofitStoreLocationRepositoryTests {
    private val api = mockk<StoreLocationApi>()
    private val repository = RetrofitStoreLocationRepository(api, mockk<Logger>(relaxed = true))

    private fun branches(vararg rows: String): Response<JsonArray> =
        Response.success(Json.parseToJsonElement("[${rows.joinToString(",")}]") as JsonArray)

    private fun row(id: Long = 7, lat: String = "null", lng: String = "null") =
        """{"id":"S","store_id":$id,"name":"Loja Demo","address":"Avenida Quistock, 101",""" +
            """"city":"Sao Paulo","state":"SP","latitude":$lat,"longitude":$lng}"""

    private suspend fun success() = (repository.read(7) as StoreLocationResult.Success).location

    @Test fun mapsAddressAndKeepsNullCoordinates() = runTest {
        coEvery { api.branches() } returns branches(row(id = 1), row())
        val location = success()
        assertEquals("Avenida Quistock, 101", location.street)
        assertEquals("Sao Paulo", location.city)
        assertEquals("SP", location.state)
        assertFalse(location.hasCoordinates)
    }

    @Test fun validCoordinatesAreKept() = runTest {
        coEvery { api.branches() } returns branches(row(lat = "-23.55", lng = "-46.63"))
        val location = success()
        assertEquals(-23.55, location.latitude!!, 0.0)
        assertEquals(-46.63, location.longitude!!, 0.0)
    }

    @Test fun incompleteOrOutOfRangeCoordinatesAreDiscarded() = runTest {
        coEvery { api.branches() } returns branches(row(lat = "-23.5"))
        assertFalse(success().hasCoordinates)
        coEvery { api.branches() } returns branches(row(lat = "91", lng = "10"))
        assertFalse(success().hasCoordinates)
        coEvery { api.branches() } returns branches(row(lat = "\"-23.5\"", lng = "\"-46.6\""))
        assertFalse(success().hasCoordinates)
    }

    @Test fun missingAddressAndCoordinatesIsNoAddress() = runTest {
        coEvery { api.branches() } returns branches(
            """{"store_id":7,"name":"Loja Demo","address":null,"city":" ","state":null,""" +
                """"latitude":null,"longitude":null}""",
        )
        assertSame(StoreLocationResult.NoAddress, repository.read(7))
    }

    @Test fun zeroOrDuplicateMatchesFailClosed() = runTest {
        coEvery { api.branches() } returns branches(row(id = 1))
        assertSame(StoreLocationResult.Failure, repository.read(7))
        coEvery { api.branches() } returns branches(row(), row())
        assertSame(StoreLocationResult.Failure, repository.read(7))
    }

    @Test fun httpAndNetworkFailuresAreFailures() = runTest {
        coEvery { api.branches() } returns Response.error(403, "".toResponseBody())
        assertSame(StoreLocationResult.Failure, repository.read(7))
        coEvery { api.branches() } throws IOException("fixture")
        assertSame(StoreLocationResult.Failure, repository.read(7))
        coEvery { api.branches() } returns Response.success<JsonArray>(null)
        assertSame(StoreLocationResult.Failure, repository.read(7))
    }

    @Test fun cancellationPropagates() = runTest {
        coEvery { api.branches() } throws CancellationException()
        try {
            repository.read(7)
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
