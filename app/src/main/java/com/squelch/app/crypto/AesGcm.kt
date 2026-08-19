package com.squelch.app.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesGcm {

    const val TAG_BITS = 128
    const val NONCE_BYTES = 12

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(NONCE_BYTES)
        SecureRandom().nextBytes(nonce)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        if (aad != null) cipher.updateAAD(aad)
        val ctAndTag = cipher.doFinal(plaintext)
        return nonce + ctAndTag
    }

    fun decrypt(key: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        require(blob.size >= NONCE_BYTES) { "ciphertext too short" }
        val nonce = blob.copyOfRange(0, NONCE_BYTES)
        val ctAndTag = blob.copyOfRange(NONCE_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ctAndTag)
    }

    fun randomKey(): ByteArray {
        val out = ByteArray(32)
        SecureRandom().nextBytes(out)
        return out
    }
}
