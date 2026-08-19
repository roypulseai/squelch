package com.squelch.app.crypto

object VaultOps {

    data class RotationResult(
        val newMnemonic: String,
        val newKDb: ByteArray,
        val newCiphertext: ByteArray,
        val oldCiphertextReplaced: Boolean
    )

    fun mergeContacts(
        existing: List<VaultPayload.ContactEntry>,
        add: List<VaultPayload.ContactEntry>
    ): List<VaultPayload.ContactEntry> {
        val byKey = existing.associateBy { it.edPub }.toMutableMap()
        for (c in add) byKey[c.edPub] = c
        return byKey.values.toList()
    }
}

fun mnemonicToExportBlob(mnemonic: String): String {
    val seed = Bip39.mnemonicToSeed(mnemonic)
    val blob = seed.copyOf(32)
    val s = java.util.Base64.getEncoder().encodeToString(blob)
    for (i in seed.indices) seed[i] = 0
    return s
}
