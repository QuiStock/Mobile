package com.quistock.quistock.data.remote.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Parse container chunks instead of treating arbitrary compressed bytes as animation markers. */
internal object StaticProfileImage {
    fun accepts(bytes: ByteArray): Boolean = when {
        bytes.size < MIN_HEADER -> false
        bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> true
        bytes.copyOfRange(0, PNG_HEADER.size).contentEquals(PNG_HEADER) -> staticPng(bytes)
        text(bytes, 0) == "RIFF" && text(bytes, 8) == "WEBP" -> staticWebp(bytes)
        else -> false
    }

    private fun staticPng(bytes: ByteArray): Boolean {
        var offset = PNG_HEADER.size
        var valid = true
        while (valid && offset + CHUNK_HEADER <= bytes.size) {
            val size = ByteBuffer.wrap(bytes, offset, 4).int.toLong()
            val type = text(bytes, offset + 4)
            valid = size >= 0 && size <= bytes.size - offset - CHUNK_HEADER && type != "acTL"
            if (valid) offset += size.toInt() + CHUNK_HEADER
        }
        return valid && offset == bytes.size
    }

    private fun staticWebp(bytes: ByteArray): Boolean {
        var offset = MIN_HEADER
        var valid = true
        while (valid && offset + WEBP_CHUNK_HEADER <= bytes.size) {
            val type = text(bytes, offset)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong()
            valid = size >= 0 && size <= bytes.size - offset - WEBP_CHUNK_HEADER
            if (type == "ANIM" || type == "ANMF") valid = false
            if (valid) offset += size.toInt() + WEBP_CHUNK_HEADER + (size % 2).toInt()
        }
        return valid && offset == bytes.size
    }

    private fun text(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)

    private val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    private const val MIN_HEADER = 12
    private const val CHUNK_HEADER = 12
    private const val WEBP_CHUNK_HEADER = 8
}
