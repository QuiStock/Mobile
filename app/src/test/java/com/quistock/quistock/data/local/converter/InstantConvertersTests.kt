package com.quistock.quistock.data.local.converter

import io.kotest.matchers.shouldBe
import org.junit.Test
import kotlin.time.Instant

class InstantConvertersTests {
    private val converter = InstantConverters()

    @Test
    fun `instant survives database conversion at millisecond precision`() {
        val instant = Instant.parse("2026-09-23T12:34:56.789Z")

        converter.toInstant(converter.fromInstant(instant)) shouldBe instant
    }

    @Test
    fun `null expiration survives database conversion`() {
        converter.fromInstant(null) shouldBe null
        converter.toInstant(null) shouldBe null
    }
}
