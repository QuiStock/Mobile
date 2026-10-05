package com.quistock.quistock.data.observability.logcat

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class LogcatLoggerTests {
    private val logger = LogcatLogger()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `info and debug include context`() {
        logger.info("loaded", mapOf("source" to "cache"))
        logger.debug("reading", mapOf("id" to 3))

        verify { Log.i("QuiStock", "loaded [source=cache]") }
        verify { Log.d("QuiStock", "reading [id=3]") }
    }

    @Test
    fun `warn and error include throwable and context`() {
        val failure = IllegalStateException("offline")
        logger.warn("retry", failure, mapOf("operation" to "refresh"))
        logger.error("failed", failure, mapOf("operation" to "refresh"))

        verify { Log.w("QuiStock", "retry [operation=refresh]", failure) }
        verify { Log.e("QuiStock", "failed [operation=refresh]", failure) }
    }

    @Test
    fun `empty context adds no suffix`() {
        logger.info("loaded")

        verify { Log.i("QuiStock", "loaded") }
    }
}
