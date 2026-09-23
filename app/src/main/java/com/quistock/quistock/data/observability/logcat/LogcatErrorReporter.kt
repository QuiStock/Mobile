package com.quistock.quistock.data.observability.logcat

import com.quistock.quistock.domain.port.ErrorReporter

class LogcatErrorReporter(private val logger: LogcatLogger) : ErrorReporter {
    override fun record(msg: String?, throwable: Throwable, context: Map<String, String>) {
        logger.error(
            msg = msg ?: "",
            context = context,
            throwable = throwable,
        )
    }
}
