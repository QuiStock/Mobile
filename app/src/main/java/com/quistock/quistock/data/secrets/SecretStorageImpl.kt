package com.quistock.quistock.data.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quistock.quistock.domain.port.SecretStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.reflect.KClass

private val Context.secretDataStore by preferencesDataStore(name = "secrets")
private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / Byte.SIZE_BITS

class SecretStorageImpl(context: Context) : SecretStorage {
    private val dataStore = context.applicationContext.secretDataStore
    private val definitions = Secrets.all.associateBy { it.type }.also { definitions ->
        require(definitions.size == Secrets.all.size) { "Duplicate secret type" }
        require(Secrets.all.map { it.key }.distinct().size == Secrets.all.size) { "Duplicate secret key" }
        require(Secrets.all.none { it.key.isBlank() }) { "Secret key must not be blank" }
    }
    private val inMemorySecrets = mutableMapOf<String, Any>()
    private val cipher = SecretCipher()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> resolveDefinition(type: KClass<T>): SecretDefinition<T> =
        definitions[type] as? SecretDefinition<T>
            ?: throw IllegalArgumentException("Unregistered secret type: $type")

    override suspend fun <T : Any> save(value: T, type: KClass<T>) {
        val definition = resolveDefinition(type)

        when (definition.storageType) {
            StorageType.MEMORY -> synchronized(inMemorySecrets) { inMemorySecrets[definition.key] = value }

            StorageType.PERSISTENT -> {
                val serialized = Json.encodeToString(definition.serializer, value).toByteArray(Charsets.UTF_8)
                val encrypted = withContext(Dispatchers.IO) { cipher.encrypt(definition.key, serialized) }
                dataStore.edit {
                    it[stringPreferencesKey(definition.key)] =
                        Base64.encodeToString(encrypted, Base64.NO_WRAP)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> read(type: KClass<T>): T? {
        val definition = resolveDefinition(type)

        when (definition.storageType) {
            StorageType.MEMORY -> synchronized(inMemorySecrets) { inMemorySecrets[definition.key] as? T }

            StorageType.PERSISTENT -> {
                val stored = dataStore.data.first()[stringPreferencesKey(definition.key)] ?: return null
                val decoded = withContext(Dispatchers.IO) {
                    cipher.decrypt(definition.key, Base64.decode(stored, Base64.NO_WRAP))
                }
                Json.decodeFromString(definition.serializer, decoded.toString(Charsets.UTF_8))
            }
        }
    }

    override suspend fun <T : Any> delete(type: KClass<T>) {
        val definition = resolveDefinition(type)

        when (definition.storageType) {
            StorageType.MEMORY -> synchronized(inMemorySecrets) { inMemorySecrets.remove(definition.key) }
            StorageType.PERSISTENT -> dataStore.edit { it.remove(stringPreferencesKey(definition.key)) }
        }
    }
}

private class SecretCipher {
    private val alias = "quistock_secrets_v1"

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    fun encrypt(secretKey: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(secretKey.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    fun decrypt(secretKey: String, payload: ByteArray): ByteArray {
        require(payload.size > GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) { "Invalid encrypted secret" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)),
        )
        cipher.updateAAD(secretKey.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size))
    }
}
