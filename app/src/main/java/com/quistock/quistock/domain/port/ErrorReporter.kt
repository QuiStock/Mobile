package com.quistock.quistock.domain.port

interface ErrorReporter {
    fun record(exception: Throwable, context: Map<String, String> = emptyMap())
}
