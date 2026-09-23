package com.quistock.quistock.data.observability

import com.quistock.quistock.domain.port.ErrorReporter

class CompositeErrorReporter(val reporters: List<ErrorReporter>) : ErrorReporter {
    override fun record(msg: String?, throwable: Throwable, context: Map<String, String>) {
        reporters.forEach {
            it.record(
                msg = msg,
                throwable = throwable,
                context = context,
            )
        }
    }
}
