package com.quistock.quistock.data.remote.auth

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Per-request jars stage response cookies; only a generation-checked durable commit publishes them. */
class AuthCookieCustody(
    private val store: AuthCookieStore,
    private val origin: HttpUrl,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private data class CookieState(val generation: Long, val cookies: List<Cookie>)

    @Volatile private var state = CookieState(0, emptyList())
    private val generation get() = state.generation
    private var cookies: List<Cookie>
        get() = state.cookies
        set(value) {
            state = state.copy(cookies = value)
        }

    suspend fun begin(next: Long) = mutex.withLock {
        state = CookieState(next, emptyList())
    }

    suspend fun restore(expected: Long): RestoreCookies = mutex.withLock {
        if (generation != expected) return@withLock RestoreCookies.MISSING
        val saved = store.read()
        if (saved.isEmpty()) return@withLock RestoreCookies.MISSING
        val restored = saved.map { it.cookie() }
        require(restored.all { allowed(it) && it.name == REFRESH }) { "Invalid credential scope" }
        val valid = restored.filter { it.expiresAt > now() }
        if (valid.isEmpty()) {
            clearLocked()
            RestoreCookies.EXPIRED
        } else {
            if (valid.size != restored.size) store.write(valid.map(RefreshCookieRecord::from))
            cookies = valid
            RestoreCookies.AVAILABLE
        }
    }

    fun access(expected: Long): String? {
        val captured = state
        return if (expected == captured.generation) {
            captured.cookies.firstOrNull { it.name == ACCESS && it.expiresAt > now() }?.value
        } else {
            null
        }
    }

    fun isCurrent(expected: Long): Boolean = generation == expected

    fun requestJar(expected: Long, refresh: Boolean, path: String = REFRESH_PATH): StagedAuthCookieJar {
        val captured = state
        return StagedAuthCookieJar(
            origin,
            if (refresh && expected == captured.generation) {
                captured.cookies.filter {
                    it.name == REFRESH && it.expiresAt > now()
                }
            } else {
                emptyList()
            },
            now,
            path,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun commit(expected: Long, received: List<Cookie>, success: Boolean): Boolean =
        withContext(NonCancellable) {
            mutex.withLock {
                if (expected != generation) return@withLock false
                require(received.all { allowed(it) && it.persistent }) { "Invalid authentication cookies" }
                val next = cookies.toMutableList()
                received.forEach { cookie ->
                    next.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                    if (cookie.expiresAt > now()) next.add(cookie)
                }
                if (success) {
                    require(
                        listOf(ACCESS, REFRESH).all { name ->
                            received.any { it.name == name && it.expiresAt > now() } &&
                                next.any { it.name == name && it.expiresAt > now() }
                        },
                    ) { "Incomplete authentication cookies" }
                }
                try {
                    if (received.isNotEmpty()) {
                        store.write(next.filter { it.name == REFRESH }.map(RefreshCookieRecord::from))
                    }
                    cookies = next
                    true
                } catch (failure: Exception) {
                    cookies = emptyList()
                    store.revoke()
                    throw failure
                }
            }
        }

    suspend fun clear(expected: Long) = withContext(NonCancellable) {
        mutex.withLock { if (expected == generation) clearLocked() }
    }

    private suspend fun clearLocked() {
        cookies = emptyList()
        store.revoke()
    }

    private fun allowed(cookie: Cookie): Boolean = cookie.name in listOf(ACCESS, REFRESH) &&
        (
            if (cookie.hostOnly) {
                origin.host == cookie.domain
            } else {
                origin.host == cookie.domain || origin.host.endsWith(".${cookie.domain}")
            }
            ) &&
        (!cookie.secure || origin.isHttps) && cookie.httpOnly

    companion object {
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val REFRESH_PATH = "/refresh"
        const val CSRF_PATH = "/csrf"
        const val CSRF = "AUTH-XSRF-TOKEN"
        const val LOGOUT_PATH = "/logout"
    }
}

enum class RestoreCookies { MISSING, EXPIRED, AVAILABLE }

class StagedAuthCookieJar(
    private val origin: HttpUrl,
    private val initial: List<Cookie>,
    private val now: () -> Long,
    private val requestPath: String = AuthCookieCustody.REFRESH_PATH,
) : CookieJar {
    private val received = mutableListOf<Cookie>()
    private val csrf = mutableListOf<Cookie>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (sameOrigin(url)) {
            synchronized(received) {
                if (url.encodedPath == AuthCookieCustody.CSRF_PATH) {
                    csrf.addAll(cookies.filter { it.name == AuthCookieCustody.CSRF })
                } else {
                    received.addAll(
                        cookies.filter { it.name in listOf(AuthCookieCustody.ACCESS, AuthCookieCustody.REFRESH) },
                    )
                }
            }
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (requestPath in listOf(AuthCookieCustody.REFRESH_PATH, AuthCookieCustody.LOGOUT_PATH) &&
            sameOrigin(url) && url.encodedPath == origin.resolve(requestPath)?.encodedPath
        ) {
            initial.filter { it.expiresAt > now() && it.matches(url) } +
                synchronized(received) { csrf.filter { eligibleCsrf(it, url) }.takeIf { it.size == 1 }.orEmpty() }
        } else {
            emptyList()
        }

    fun received(): List<Cookie> = synchronized(received) { received.toList() }
    fun hasRefresh(url: HttpUrl): Boolean = loadForRequest(url).any { it.name == AuthCookieCustody.REFRESH }
    fun hasCsrf(url: HttpUrl): Boolean = synchronized(received) {
        sameOrigin(url) && url.encodedPath == AuthCookieCustody.REFRESH_PATH &&
            csrf.size == 1 && eligibleCsrf(csrf.single(), url)
    }
    fun discardCsrf() = synchronized(received) { csrf.clear() }
    private fun eligibleCsrf(cookie: Cookie, url: HttpUrl) = cookie.httpOnly &&
        cookie.value.isNotEmpty() && cookie.expiresAt > now() && cookie.matches(url)
    private fun sameOrigin(url: HttpUrl) =
        url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port
}
