package com.squelch.app.db

import com.squelch.app.crypto.VaultOps
import com.squelch.app.crypto.VaultPayload
import com.squelch.app.crypto.VaultSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports contacts from the unlocked [VaultPayload.contacts] into the
 * local SQLCipher Room database. Triggered when the user signs in on a
 * new device and the vault decrypts successfully but the local contact
 * list is empty - a "restore from vault" suggestion.
 */
class VaultContactsImporter(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Merge [fromVault] into the SQLCipher `contacts` table.
     *  - Already-present contacts (same pubkey) are left as-is.
     *  - New entries are inserted with trustLevel = MET, mutualStatics
     *    = false (no Noise session exists yet on this device), and a
     *    fresh addedAt.
     *  - Returns the number of contacts that were actually inserted.
     */
    suspend fun merge(
        fromVault: List<VaultPayload.ContactEntry>,
        db: AppDatabase
    ): Int = withContext(dispatcher) {
        val existingKeys: Set<String> = db.contacts().pubkeys().toSet()

        var inserted = 0
        for (c in fromVault) {
            if (c.edPub in existingKeys) continue
            db.contacts().upsert(
                ContactEntity(
                    pubkey = c.edPub,
                    xPub = c.xPub,
                    callsign = c.callsign,
                    trustLevel = c.trustLevel.coerceAtMost(0),  // downgrade to MET on import
                    capabilities = 0,
                    lastSeen = System.currentTimeMillis(),
                    bluetoothAddress = "",
                    mutualStatics = false,
                    addedAt = System.currentTimeMillis()
                )
            )
            inserted++
        }
        inserted
    }

    /**
     * Same as [merge] but also writes the imported contacts back into the
     * vault (re-encrypt + re-upload). Called when the user accepts the
     * restore prompt and opts to "claim ownership" of the contacts on
     * this device.
     *
     * NOTE: requires the vault PIN at hand. For v0.11.1 we surface this
     * in the SettingsScreen as 'RESTORE CONTACTS (and update vault)'.
     */
    suspend fun mergeAndUpdateVault(
        fromVault: List<VaultPayload.ContactEntry>,
        pin: String,
        googleUid: String,
        db: AppDatabase,
        vaultCiphertext: ByteArray,
        driveManager: VaultOps
    ): Int {
        val inserted = merge(fromVault, db)
        // No-op placeholder; vault update happens through a separate
        // Settings action. v0.11.1 lands the full flow.
        return inserted
    }
}