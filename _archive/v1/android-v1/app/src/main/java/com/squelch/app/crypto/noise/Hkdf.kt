package com.squelch.app.crypto.noise

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** SHA-256, HMAC-SHA256 and HKDF-SHA256 as required by the Noise spec (sections 4.3, 5). */
object Hkdf {
    const val HASHLEN = 32
    private const val BLOCKLEN = 64

    fun hash(data: ByteArray): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(data)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * HKDF as defined in Noise: salt = chaining_key, info = zero-length.
     * Returns numOutputs HASHLEN-byte outputs.
     */
    fun hkdf(chainingKey: ByteArray, inputKeyMaterial: ByteArray, numOutputs: Int): List<ByteArray> {
        require(numOutputs in 2..3)
        val tempKey = hmac(chainingKey, inputKeyMaterial)
        val outputs = ArrayList<ByteArray>(numOutputs)
        var acc = ByteArray(0)
        for (i in 1..numOutputs) {
            acc = hmac(tempKey, acc + byteArrayOf(i.toByte()))
            outputs.add(acc)
        }
        return outputs
    }
}
