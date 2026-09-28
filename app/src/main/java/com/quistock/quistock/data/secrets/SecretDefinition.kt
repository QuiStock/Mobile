package com.quistock.quistock.data.secrets

import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

data class SecretDefinition<T : Any>(
    val type: KClass<T>,
    val key: String,
    val storageType: StorageType,
    val serializer: KSerializer<T>,
)
