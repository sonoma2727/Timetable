package io.github.sonoma2727.timetable.data.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {

    private const val TAG = "CryptoManager"
    private const val KEY_ALIAS = "timetable_secret"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val KEY_PREFIX = "k:"
    private const val PLAIN_PREFIX = "p:"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keystoreEncrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return ByteArray(IV_SIZE + encrypted.size).also {
            iv.copyInto(it, 0)
            encrypted.copyInto(it, IV_SIZE)
        }
    }

    private fun keystoreDecrypt(data: ByteArray): ByteArray {
        require(data.size > IV_SIZE) { "ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, data, 0, IV_SIZE),
        )
        return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }

    fun encryptToString(plain: String): String = try {
        KEY_PREFIX + java.util.Base64.getEncoder()
            .encodeToString(keystoreEncrypt(plain.toByteArray(Charsets.UTF_8)))
    } catch (t: Throwable) {
        Log.e(TAG, "keystore encrypt failed, fallback to local obfuscation", t)
        PLAIN_PREFIX + java.util.Base64.getEncoder()
            .encodeToString(plain.toByteArray(Charsets.UTF_8))
    }

    fun decryptFromString(encoded: String): String {
        if (encoded.startsWith(PLAIN_PREFIX)) {
            val data = java.util.Base64.getDecoder().decode(encoded.substring(PLAIN_PREFIX.length))
            return String(data, Charsets.UTF_8)
        }
        val payload = if (encoded.startsWith(KEY_PREFIX)) {
            java.util.Base64.getDecoder().decode(encoded.substring(KEY_PREFIX.length))
        } else {
            java.util.Base64.getDecoder().decode(encoded)
        }
        return String(keystoreDecrypt(payload), Charsets.UTF_8)
    }
}
