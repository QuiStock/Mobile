package com.quistock.quistock.data.remote.auth

import kotlinx.serialization.Serializable
import okhttp3.Cookie

interface AuthCookieStore {
    suspend fun read(): List<RefreshCookieRecord>
    suspend fun write(records: List<RefreshCookieRecord>)

    /** Destroy the dedicated encryption key before removing a discarded credential. */
    suspend fun revoke()
}

@Serializable
class RefreshCookieRecord(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val hostOnly: Boolean,
    val secure: Boolean,
    val httpOnly: Boolean,
    val version: Int = 1,
) {
    fun cookie(): Cookie {
        require(version == 1) { "Unsupported credential version" }
        val builder = Cookie.Builder().name(name).value(value).path(path).expiresAt(expiresAt)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }

    companion object {
        fun from(cookie: Cookie) = RefreshCookieRecord(
            cookie.name,
            cookie.value,
            cookie.domain,
            cookie.path,
            cookie.expiresAt,
            cookie.hostOnly,
            cookie.secure,
            cookie.httpOnly,
        )
    }
}

@Serializable
class RefreshCookieBatch(val records: List<RefreshCookieRecord>, val version: Int = 1)
