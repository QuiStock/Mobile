package com.quistock.quistock.data.observability

import com.quistock.quistock.domain.port.ErrorReporter

class CompositeErrorReporter(val reporters: List<ErrorReporter>) : ErrorReporter {
    override fun record(exception: Throwable, context: Map<String, String>) {
        reporters.forEach {
            it.record(
                exception = exception,
                context = context,
            )
        }
    }
}
