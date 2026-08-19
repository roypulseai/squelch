package com.squelch.app.crypto

import java.security.MessageDigest

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

    fun decryptVault(pin: String, googleUid: String, blob: ByteArray): VaultPayload {
        val kVault = deriveKVault(pin, googleUid)
        val plaintext = AesGcm.decrypt(kVault, blob, AAD.toByteArray())
        return VaultPayload.fromJsonString(String(plaintext, Charsets.UTF_8))
    }

    fun vaultFingerprint(googleUid: String): String =
        sha256(googleUid.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
