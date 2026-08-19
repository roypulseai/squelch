package com.squelch.app.crypto.noise

import com.squelch.app.util.Bytes

class CipherState(
    private var key: ByteArray? = null,
    private var n: Long = 0L
) {
    companion object {
        private const val MAX_NONCE = -1L
    }

    fun hasKey(): Boolean = key != null

    fun setNonce(value: Long) { n = value }

    fun encryptWithAd(ad: ByteArray, plaintext: ByteArray): ByteArray {
        val k = key ?: return plaintext
        if (n == MAX_NONCE) throw IllegalStateException("Noise nonce exhausted")
        val ct = AesGcm.encrypt(k, n, ad, plaintext)
        n++
        return ct
    }

    fun decryptWithAd(ad: ByteArray, ciphertext: ByteArray): ByteArray {
        val k = key ?: return ciphertext
        if (n == MAX_NONCE) throw IllegalStateException("Noise nonce exhausted")
        val pt = AesGcm.decrypt(k, n, ad, ciphertext)
        n++
        return pt
    }

    fun rekey() {
        val k = key ?: return
        val zeros = ByteArray(32)
        val ct = AesGcm.encrypt(k, MAX_NONCE, ByteArray(0), zeros)
        key = Bytes.concat(ct).copyOf(32)
    }

    fun rekeyWithNew(newKey: ByteArray) {
        key = newKey
        n = 0
    }
}
