package com.quistock.quistock.data.secrets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / Byte.SIZE_BITS

internal class SecretCipher(private val alias: String) {

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
