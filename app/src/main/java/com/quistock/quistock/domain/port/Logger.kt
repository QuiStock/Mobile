package com.quistock.quistock.domain.port

interface Logger {
    fun info(msg: String)
    fun debug(msg: String, context: Map<String, Any>? = null)
    fun warn(msg: String, exception: Exception?)
    fun error(msg: String, throwable: Throwable, context: Map<String, Any>? = null)
}
