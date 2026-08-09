package com.squelch.app.crypto.noise

import com.squelch.app.util.Bytes

/**
 * Noise CipherState (spec 5.1): a 32-byte key and 64-bit nonce counter.
 * An empty key (null) means cleartext passthrough.
 */
class CipherState(
    private var key: ByteArray? = null,
    private var n: Long = 0L
) {
    companion object {
        private const val MAX_NONCE = -1L // 2^64 - 1
    }

    fun hasKey(): Boolean = key != null

    fun setNonce(value: Long) {
        n = value
    }

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

    /** REKEY(k): k = first 32 bytes of ENCRYPT(k, maxnonce, zerolen, zeros32). */
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
