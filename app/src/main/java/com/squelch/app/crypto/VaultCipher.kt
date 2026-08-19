package com.squelch.app.crypto

import java.security.MessageDigest

object VaultCipher {

    const val AAD = "squ|vault|v1"

    fun deriveKey(googleUid: String): ByteArray {
        val input = "squelch_vault_v1:$googleUid".toByteArray(Charsets.UTF_8)
        return sha256(input)
    }

    fun deriveKDb(googleUid: String): ByteArray = sha256(deriveKey(googleUid))

    fun encryptVault(googleUid: String, payload: VaultPayload): ByteArray {
        val key = deriveKey(googleUid)
        return AesGcm.encrypt(key, payload.toJsonString().toByteArray(Charsets.UTF_8), AAD.toByteArray())
    }

    fun decryptVault(googleUid: String, blob: ByteArray): VaultPayload {
        val key = deriveKey(googleUid)
        val plaintext = AesGcm.decrypt(key, blob, AAD.toByteArray())
        return VaultPayload.fromJsonString(String(plaintext, Charsets.UTF_8))
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
