package com.actuate.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.actuate.domain.repository.SecretStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts secrets (API keys, tokens) with an AES-256-GCM key that never
 * leaves the Android Keystore. Ciphertext is persisted in SharedPreferences.
 * Plain secrets never touch disk or logs.
 */
class KeystoreSecretStore(context: Context) : SecretStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun save(key: String, value: String) {
        if (value.isEmpty()) {
            delete(key)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stored = EncryptedPayload(cipher.iv, encrypted)
        prefs.edit { putString(key, stored.encode()) }
    }

    override fun read(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        val payload = runCatching { EncryptedPayload.decode(encoded) }.getOrNull() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, payload.iv))
            String(cipher.doFinal(payload.ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun delete(key: String) {
        prefs.edit { remove(key) }
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray) {
        fun encode(): String {
            val data = ByteArray(1 + iv.size + ciphertext.size).apply {
                this[0] = iv.size.toByte()
                System.arraycopy(iv, 0, this, 1, iv.size)
                System.arraycopy(ciphertext, 0, this, 1 + iv.size, ciphertext.size)
            }
            return Base64.encodeToString(data, Base64.NO_WRAP)
        }

        companion object {
            fun decode(encoded: String): EncryptedPayload {
                val data = Base64.decode(encoded, Base64.NO_WRAP)
                val ivSize = data[0].toInt() and 0xFF
                val iv = data.copyOfRange(1, 1 + ivSize)
                val ciphertext = data.copyOfRange(1 + ivSize, data.size)
                return EncryptedPayload(iv, ciphertext)
            }
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "actuate_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val PREFS_NAME = "actuate_secrets"
    }
}