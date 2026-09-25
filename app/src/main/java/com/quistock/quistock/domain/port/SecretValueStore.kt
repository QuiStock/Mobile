package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.SecretValue

interface SecretValueStore {
    fun save(secret: SecretValue)
    fun read(key: String): SecretValue?
    fun delete(key: String): Boolean
    fun prune()
}
