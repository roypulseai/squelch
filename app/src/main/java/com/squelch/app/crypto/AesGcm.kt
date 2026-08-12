package com.squelch.app.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM helper with the JCE default provider (Android platform).
 *
 * Format on the wire (lengths in bytes):
 *   [12-byte nonce] [16-byte auth tag concatenated to ciphertext]
 *
 * So `encrypt(key, plaintext) -> nonce ++ ct+tag`, and `decrypt(key, blob)
 * -> plaintext` if the tag verifies.
 *
 * The vault layout uses nonce||ct+tag with a fresh nonce per encrypt().
 */
object AesGcm {

    const val TAG_BITS = 128
    const val NONCE_BYTES = 12

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Nonce per encrypt(): 12 bytes from a CSPRNG.
        val nonce = ByteArray(NONCE_BYTES)
        java.security.SecureRandom().nextBytes(nonce)
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
        require(blob.size >= NONCE_BYTES) { "vault ciphertext too short" }
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

    /** Generate a fresh 32-byte AES key (use only for tests; the real vault
     *  key is derived via Argon2id + SHA-256 from PIN + Google UID). */
    fun randomKey(): ByteArray {
        val out = ByteArray(32)
        java.security.SecureRandom().nextBytes(out)
        return out
    }
}
