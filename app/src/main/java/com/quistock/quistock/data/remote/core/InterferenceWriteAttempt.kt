package com.quistock.quistock.data.remote.core

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** A write must never be replayed by an HTTP follow-up, even after a 401. */
class InterferenceWriteAttempt {
    private var sent = false

    fun send(chain: Interceptor.Chain): Response {
        if (chain.request().method != "GET") {
            if (sent) throw IOException("Action write follow-up blocked")
            sent = true
        }
        return chain.proceed(chain.request())
    }
}
