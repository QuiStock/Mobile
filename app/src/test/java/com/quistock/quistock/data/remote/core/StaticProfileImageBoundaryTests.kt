package com.quistock.quistock.data.remote.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StaticProfileImageBoundaryTests {
    @Test fun recognizesJpegAndRejectsWrongSignatures() {
        val jpeg = ByteArray(12).apply {
            this[0] = 0xff.toByte()
            this[1] = 0xd8.toByte()
        }
        assertTrue(StaticProfileImage.accepts(jpeg))
        jpeg[1] = 0
        assertFalse(StaticProfileImage.accepts(jpeg))
        assertFalse(StaticProfileImage.accepts("RIFF0000NOPE".toByteArray()))
        assertFalse(StaticProfileImage.accepts("NOPE0000WEBP".toByteArray()))
    }

    @Test fun pngRejectsNegativeOversizedAndTruncatedChunks() {
        val header = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val emptyChunk = ByteBuffer.allocate(12).putInt(0).put("IEND".toByteArray()).putInt(0).array()
        assertTrue(StaticProfileImage.accepts(header + emptyChunk))
        for (size in listOf(-1, 100)) {
            val chunk = ByteBuffer.allocate(12).putInt(size).put("IDAT".toByteArray()).putInt(0).array()
            assertFalse(StaticProfileImage.accepts(header + chunk))
        }
        assertFalse(StaticProfileImage.accepts(header + byteArrayOf(0, 0, 0, 0)))
    }

    @Test fun webpEnforcesDeclaredChunkLengthAndPadding() {
        val header = "RIFF0000WEBP".toByteArray()
        val chunk = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .put("VP8 ".toByteArray()).putInt(1).put(1.toByte()).put(0.toByte()).array()
        assertTrue(StaticProfileImage.accepts(header + chunk))
        assertFalse(StaticProfileImage.accepts(header + chunk.copyOf(9)))
        for (size in listOf(-1, 100)) {
            val invalid = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .put("VP8 ".toByteArray()).putInt(size).array()
            assertFalse(StaticProfileImage.accepts(header + invalid))
        }
    }
}
