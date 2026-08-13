package com.squelch.app.crypto

/**
 * App-level vault operations on top of [VaultCipher] + [VaultSession].
 *
 *  - changePin(oldPin, newPin, googleUid): re-encrypt the payload with
 *    a new K_vault derived from newPin, re-upload to Drive, swap the
 *    in-memory passphrase, and re-open the SQLCipher database with
 *    K_db derived from the new K_vault.
 *  - exportMnemonic(): returns the unlocked 24-word phrase (caller is
 *    responsible for clearing the resulting String when finished).
 */
object VaultOps {

    /** Result of a [changePin] call. */
    data class RotationResult(
        val newMnemonic: String,
        val newKDb: ByteArray,
        val newCiphertext: ByteArray,
        val oldCiphertextReplaced: Boolean
    )

    /**
     * Verify [oldPin] against the in-memory vault. Derive a new
     * [VaultCipher] key from [newPin] + [googleUid]. Re-encrypt the
     * existing payload (mnemonic + contacts + settings). Caller is
     * responsible for uploading the new ciphertext + swapping the DB.
     *
     * Throws [javax.crypto.AEADBadTagException] / BadPaddingException
     * when [oldPin] is wrong, surfaced as [WrongPinException].
     */
    fun preparePinRotation(
        oldPin: String,
        newPin: String,
        googleUid: String,
        ciphertextOnDrive: ByteArray
    ): RotationResult {
        // 1. Validate oldPin by decrypting the current vault.
        val currentPayload = VaultCipher.decryptVault(oldPin, googleUid, ciphertextOnDrive)

        // 2. Derive new keys + re-encrypt with newPin.
        val newKVault = VaultCipher.deriveKVault(newPin, googleUid)
        val newKDb = VaultCipher.deriveKDb(newKVault)
        val newCiphertext = VaultCipher.encryptVault(newPin, googleUid, currentPayload)

        return RotationResult(
            newMnemonic = currentPayload.mnemonic,
            newKDb = newKDb,
            newCiphertext = newCiphertext,
            oldCiphertextReplaced = true
        )
    }

    /**
     * Convenience: build the contact JSON for [VaultPayload.contacts]
     * from a list of [VaultPayload.ContactEntry].
     */
    fun mergeContacts(
        existing: List<VaultPayload.ContactEntry>,
        add: List<VaultPayload.ContactEntry>
    ): List<VaultPayload.ContactEntry> {
        val byKey = existing.associateBy { it.edPub }.toMutableMap()
        for (c in add) byKey[c.edPub] = c
        return byKey.values.toList()
    }

    class WrongPinException(cause: Throwable) : RuntimeException("wrong PIN", cause)
}

/** Convenience: a safe accessor for the unlocked mnemonic (or null if locked). */
fun unlockedMnemonic(): String? = VaultSession.mnemonicOrNull()

/** Convert the 24-word mnemonic to a base64 of its 32-byte seed for
 *  export-bundle purposes. The caller is responsible for clearing the
 *  returned String when done. */
fun mnemonicToExportBlob(mnemonic: String): String {
    val seed = Bip39.mnemonicToSeed(mnemonic)
    val blob = seed.copyOf(32)
    val s = java.util.Base64.getEncoder().encodeToString(blob)
    // Zero the working copy.
    for (i in seed.indices) seed[i] = 0
    return s
}