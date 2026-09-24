package com.quistock.quistock.domain.port

interface ErrorReporter {
    fun record(throwable: Throwable, context: Map<String, String> = emptyMap())
}
