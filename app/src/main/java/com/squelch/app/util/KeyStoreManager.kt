package com.squelch.app.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyStoreManager {
    private const val TAG = "KeyStoreManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "squelch_identity_key"

    fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getEntry(KEY_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val kg = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun encrypt(plaintext: ByteArray): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val combined = ByteArray(4 + iv.size + ciphertext.size)
        System.arraycopy(intToBytes(iv.size), 0, combined, 0, 4)
        System.arraycopy(iv, 0, combined, 4, iv.size)
        System.arraycopy(ciphertext, 0, combined, 4 + iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): ByteArray {
        val key = getOrCreateKey()
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = bytesToInt(combined, 0)
        val iv = combined.copyOfRange(4, 4 + ivSize)
        val ciphertext = combined.copyOfRange(4 + ivSize, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    fun hasKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getEntry(KEY_ALIAS, null) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun intToBytes(v: Int): ByteArray = byteArrayOf(
        ((v ushr 24) and 0xff).toByte(),
        ((v ushr 16) and 0xff).toByte(),
        ((v ushr 8) and 0xff).toByte(),
        (v and 0xff).toByte()
    )

    private fun bytesToInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xff) shl 24) or
        ((b[offset + 1].toInt() and 0xff) shl 16) or
        ((b[offset + 2].toInt() and 0xff) shl 8) or
        (b[offset + 3].toInt() and 0xff)
}
