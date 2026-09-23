package com.quistock.quistock.domain.port

interface ErrorReporter {
    fun record(msg: String? = null, throwable: Throwable, context: Map<String, String> = emptyMap())
}
