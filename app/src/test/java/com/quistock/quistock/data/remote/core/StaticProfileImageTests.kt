package com.quistock.quistock.data.remote.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StaticProfileImageTests {
    @Test fun rejectsGifAndTruncatedImages() {
        assertFalse(StaticProfileImage.accepts("GIF89a fixture".toByteArray()))
        assertFalse(StaticProfileImage.accepts(byteArrayOf(0xff.toByte(), 0xd8.toByte())))
    }

    @Test fun rejectsAnimatedWebpAndAcceptsStaticContainer() {
        assertFalse(StaticProfileImage.accepts(webp("ANIM")))
        assertFalse(StaticProfileImage.accepts(webp("ANMF")))
        assertTrue(StaticProfileImage.accepts(webp("VP8 ")))
    }

    @Test fun rejectsAnimatedPngContainer() {
        val header = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val chunk = ByteBuffer.allocate(12).putInt(0).put("acTL".toByteArray()).putInt(0).array()
        assertFalse(StaticProfileImage.accepts(header + chunk))
    }

    private fun webp(chunk: String): ByteArray = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".toByteArray()).putInt(12).put("WEBP".toByteArray())
        .put(chunk.toByteArray()).putInt(0).array()
}
