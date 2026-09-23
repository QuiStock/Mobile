package com.quistock.quistock.data.observability.logcat

import android.util.Log
import com.quistock.quistock.domain.port.Logger

private const val APPLICATION_BASE_TAG = "QuiStock"

class LogcatLogger : Logger {
    private fun contextToString(context: Map<String, Any>?): String = context
        ?.entries
        ?.joinToString { "${it.key}=${it.value}" }
        ?.let { " [$it]" }
        ?: ""

    override fun info(msg: String) {
        Log.i(APPLICATION_BASE_TAG, msg)
    }

    override fun debug(msg: String, context: Map<String, Any>?) {
        Log.d(
            APPLICATION_BASE_TAG,
            msg + contextToString(context),
        )
    }

    override fun warn(msg: String, exception: Exception?) {
        Log.w(
            APPLICATION_BASE_TAG,
            msg,
            exception,
        )
    }

    override fun error(msg: String, throwable: Throwable, context: Map<String, Any>?) {
        Log.e(
            APPLICATION_BASE_TAG,
            msg + contextToString(context),
            throwable,
        )
    }
}
