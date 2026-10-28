package com.quistock.quistock.data.remote.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Synthetic transport: no sockets, credentials, Cloudinary account or device storage. */
internal class CloudinaryUploadFixture {
    val request = slot<Request>()
    val calls = mockk<Call.Factory>()
    val call = mockk<Call>(relaxed = true)
    var status = 200
    var headers = Headers.Builder().build()
    var body = envelope()
    var respond: (Callback) -> Unit = { callback -> callback.onResponse(call, response()) }
    val repository = CloudinaryPhotoRepository("fixture", "fixture-preset", calls)

    init {
        every { calls.newCall(capture(request)) } returns call
        every { call.enqueue(any()) } answers { respond(firstArg()) }
    }

    private fun response(): Response = Response.Builder()
        .request(request.captured).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
        .headers(headers).body(body.toResponseBody()).build()

    fun envelope(
        url: String = "https://images.example.test/new.jpg",
        bytes: String = "128",
        format: String = "jpg",
    ): String = """{"secure_url":"$url","bytes":$bytes,"format":"$format"}"""
}
