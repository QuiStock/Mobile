package com.quistock.quistock.domain.port

import kotlin.reflect.KClass

interface SecretStorage {
    suspend fun <T : Any> save(value: T, type: KClass<T>)
    suspend fun <T : Any> read(type: KClass<T>): T?
    suspend fun <T : Any> delete(type: KClass<T>)
}

suspend inline fun <reified T : Any> SecretStorage.save(value: T) = save(value = value, type = T::class)
suspend inline fun <reified T : Any> SecretStorage.read(): T? = read(T::class)
suspend inline fun <reified T : Any> SecretStorage.delete() = delete(T::class)
