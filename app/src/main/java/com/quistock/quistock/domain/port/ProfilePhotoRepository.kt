package com.quistock.quistock.domain.port

interface ProfilePhotoRepository {
    suspend fun read(url: String): ByteArray?
}
