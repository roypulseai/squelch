package com.squelch.app.crypto

object VaultOps {

    fun mergeContacts(
        existing: List<VaultPayload.ContactEntry>,
        add: List<VaultPayload.ContactEntry>
    ): List<VaultPayload.ContactEntry> {
        val byKey = existing.associateBy { it.edPub }.toMutableMap()
        for (c in add) byKey[c.edPub] = c
        return byKey.values.toList()
    }
}
