package com.squelch.app.crypto.noise

import com.squelch.app.util.Bytes
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM with the 96-bit nonce construction Noise uses (4 zero bytes + 8-byte BE counter). */
object AesGcm {
    private const val KEY_LEN = 32
    private const val TAG_BITS = 128

    fun encrypt(key: ByteArray, nonce64: Long, ad: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == KEY_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce(nonce64)))
        cipher.updateAAD(ad)
        return cipher.doFinal(plaintext)
    }

    fun decrypt(key: ByteArray, nonce64: Long, ad: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == KEY_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce(nonce64)))
        cipher.updateAAD(ad)
        return cipher.doFinal(ciphertext)
    }

    private fun nonce(n: Long): ByteArray {
        val out = ByteArray(12)
        System.arraycopy(Bytes.u64be(n), 0, out, 4, 8)
        return out
    }
}
