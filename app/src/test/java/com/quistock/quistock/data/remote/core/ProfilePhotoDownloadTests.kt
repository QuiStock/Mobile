package com.quistock.quistock.data.remote.core

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ProfilePhotoDownloadTests {
    private var status = 200
    private var body: () -> ResponseBody = { byteArrayOf(1, 2).toResponseBody() }
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(status).message("fixture").body(body()).build()
    }.build()
    private val repository = HttpProfilePhotoRepository(client)

    @Test fun publicHttpsImageReturnsBytesWithoutARealConnection() = runTest {
        assertArrayEquals(byteArrayOf(1, 2), repository.read("https://images.example.test/a.jpg"))
    }

    @Test fun unsafeOrUnparseableUrlsAreRejected() = runTest {
        for (url in listOf("bad", "http://images.example.test/a", "https://u:p@images.example.test/a")) {
            assertNull(repository.read(url))
        }
        assertNull(repository.read("https://:p@images.example.test/a"))
    }

    @Test fun unavailableAndOversizedImagesKeepPlaceholder() = runTest {
        status = 404
        assertNull(repository.read("https://images.example.test/a"))
        status = 200
        body = { ByteArray(2 * 1024 * 1024 + 1).toResponseBody() }
        assertNull(repository.read("https://images.example.test/a"))
    }

    @Test fun unknownLengthStreamsRemainBounded() = runTest {
        body = {
            object : ResponseBody() {
                private val content = okio.Buffer().write(ByteArray(2 * 1024 * 1024 + 1))
                override fun contentType() = null
                override fun contentLength(): Long = -1
                override fun source() = content
            }
        }
        assertNull(repository.read("https://images.example.test/a"))
    }

    @Test fun networkFailureKeepsPlaceholder() = runTest {
        val failing = OkHttpClient.Builder().addInterceptor { throw IOException("fixture") }.build()
        assertNull(HttpProfilePhotoRepository(failing).read("https://images.example.test/a"))
    }
}
