package com.quistock.quistock.data.observability.logcat

import android.util.Log
import com.quistock.quistock.domain.port.ErrorReporter

class LogcatErrorReporter : ErrorReporter {
    override fun record(exception: Throwable, context: Map<String, String>) {
        Log.e(
            "QuiStockError",
            context.entries.joinToString { "${it.key}=${it.value}" },
            exception,
        )
    }
}
