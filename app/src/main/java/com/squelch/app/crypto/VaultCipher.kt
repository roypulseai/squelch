package com.squelch.app.crypto

import java.security.MessageDigest

/**
 * Full vault encrypt/decrypt pipeline (spec §2.2):
 *   salt     = SHA-256(GoogleUID)             // 32 bytes
 *   raw_key  = Argon2id(PIN, salt, len=32)    // 32 bytes
 *   K_vault  = SHA-256(raw_key)                // clean, fixed-length 32 bytes
 *   blob     = AES-GCM-encrypt(K_vault, JSON(payload), aad = vault_version)
 *
 * The 12-byte AES-GCM nonce is prepended to the ciphertext. AAD binds the
 * payload to the v2 vault schema (currently "{squ|vault|v1}") so a future
 * versioned migration can refuse to decrypt older blobs unless explicitly
 * told.
 *
 * `K_db = SHA-256(K_vault)` (not exposed here; SQLCipher layer uses it).
 */
object VaultCipher {

    const val AAD = "squ|vault|v1"

    fun deriveKVault(pin: String, googleUid: String): ByteArray {
        val salt = sha256(googleUid.toByteArray(Charsets.UTF_8))
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        val rawKey = Argon2id.derive(password = pinBytes, salt = salt, outBytes = 32)
        return sha256(rawKey)
    }

    fun deriveKDb(kVault: ByteArray): ByteArray = sha256(kVault)

    fun encryptVault(pin: String, googleUid: String, payload: VaultPayload): ByteArray {
        val kVault = deriveKVault(pin, googleUid)
        return AesGcm.encrypt(kVault, payload.toJsonString().toByteArray(Charsets.UTF_8), AAD.toByteArray())
    }

    /**
     * @throws javax.crypto.AEADBadTagException if the PIN is wrong (tag verification fails).
     */
    fun decryptVault(pin: String, googleUid: String, blob: ByteArray): VaultPayload {
        val kVault = deriveKVault(pin, googleUid)
        val plaintext = AesGcm.decrypt(kVault, blob, AAD.toByteArray())
        return VaultPayload.fromJsonString(String(plaintext, Charsets.UTF_8))
    }

    /** Compute deterministically. Used in tests. */
    fun vaultFingerprint(googleUid: String): String =
        sha256(googleUid.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
