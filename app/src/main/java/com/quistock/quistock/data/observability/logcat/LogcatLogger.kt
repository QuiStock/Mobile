package com.quistock.quistock.data.observability.logcat

import android.util.Log
import com.quistock.quistock.domain.port.Logger

private const val APPLICATION_BASE_TAG = "QuiStock"

class LogcatLogger : Logger {
    private fun contextToString(context: Map<String, Any>): String = if (context.isEmpty()) {
        ""
    } else {
        context.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
    }

    override fun info(msg: String, context: Map<String, Any>) {
        Log.i(APPLICATION_BASE_TAG, msg + contextToString(context))
    }

    override fun debug(msg: String, context: Map<String, Any>) {
        Log.d(
            APPLICATION_BASE_TAG,
            msg + contextToString(context),
        )
    }

    override fun warn(msg: String, throwable: Throwable?, context: Map<String, Any>) {
        Log.w(
            APPLICATION_BASE_TAG,
            msg + contextToString(context),
            throwable,
        )
    }

    override fun error(msg: String, throwable: Throwable?, context: Map<String, Any>) {
        Log.e(
            APPLICATION_BASE_TAG,
            msg + contextToString(context),
            throwable,
        )
    }
}
