package com.quistock.quistock.domain.port

interface Logger {
    fun info(msg: String, context: Map<String, Any> = emptyMap())
    fun debug(msg: String, context: Map<String, Any> = emptyMap())
    fun warn(msg: String, throwable: Throwable? = null, context: Map<String, Any> = emptyMap())
    fun error(msg: String, throwable: Throwable? = null, context: Map<String, Any> = emptyMap())
}
