package com.squelch.app.db

import androidx.annotation.GuardedBy

/**
 * In-memory holder for the SQLCipher passphrase `K_db`.
 *
 *     K_db = SHA-256(K_vault)
 *     K_vault = SHA-256(Argon2id(PIN, salt = SHA-256(GoogleUID)))
 *
 * We never persist K_db to disk. It is populated by the (M6) PIN entry
 * flow (after a successful `VaultCipher.decryptVault(...)`) and is
 * explicitly cleared when the app is "locked" (Settings -> Lock, or
 * automatic after a period of inactivity).
 *
 * `isUnlocked` is the only state the rest of the app keys off of: when
 * it's true, the singleton [AppDatabase] is open and DAOs are usable.
 */
object Keyring {

    @GuardedBy("this")
    private var kDb: ByteArray? = null

    @Volatile
    private var openedAt: Long = 0L

    /** Currently exposed copy of K_db (read-only). Empty array when locked. */
    fun kDbOrEmpty(): ByteArray = synchronized(this) {
        kDb?.copyOf() ?: ByteArray(0)
    }

    fun isUnlocked(): Boolean = synchronized(this) {
        kDb != null
    }

    fun openedAt(): Long = openedAt

    fun unlock(passphrase: ByteArray) {
        require(passphrase.size == 32) { "K_db must be 32 bytes" }
        synchronized(this) {
            kDb = passphrase.copyOf()
            openedAt = System.currentTimeMillis()
        }
    }

    /** Wipes the in-memory key. Once called, [kDbOrEmpty] returns empty. */
    fun lock() {
        synchronized(this) {
            kDb?.fill(0)
            kDb = null
        }
    }

    /** Replace the current passphrase (M9+: password rotation). */
    fun rotate(newPassphrase: ByteArray) {
        require(newPassphrase.size == 32) { "K_db must be 32 bytes" }
        lock()
        unlock(newPassphrase)
    }
}
