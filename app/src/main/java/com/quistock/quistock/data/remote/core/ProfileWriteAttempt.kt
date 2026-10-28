package com.quistock.quistock.data.remote.core

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Blocks OkHttp's implicit 408/503 follow-ups, allowing only Core's explicit 401 renewal. */
class ProfileWriteAttempt {
    private var count = 0
    private var status: Int? = null

    fun send(chain: Interceptor.Chain): Response {
        if (count > 0 && status != 401) throw IOException("Profile write follow-up blocked")
        if (count >= 2) throw IOException("Profile write retry exhausted")
        count++
        status = null
        return chain.proceed(chain.request()).also { status = it.code }
    }
}
