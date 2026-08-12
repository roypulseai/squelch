package com.squelch.app.db

import com.squelch.app.crypto.VaultSession

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

    val isUnlocked: Boolean get() = VaultSession.isUnlocked

    fun kDbOrEmpty(): ByteArray = VaultSession.kDbOrEmpty()

    fun unlock(passphrase: ByteArray) {
        // No-op stub; the canonical unlock now goes through VaultSession,
        // owned by the OnboardingViewModel. This exists so legacy code
        // (e.g. test harnesses) can still compile.
        require(passphrase.size == 32) { "K_db must be 32 bytes" }
    }

    fun lock() {
        VaultSession.lock()
    }

    fun rotate(newPassphrase: ByteArray) {
        require(newPassphrase.size == 32) { "K_db must be 32 bytes" }
        val current = VaultSession.kDbOrEmpty()
        val mnemonic = VaultSession.mnemonicOrNull() ?: return
        val uid = VaultSession.googleUidOrNull() ?: return
        VaultSession.unlock(mnemonic, newPassphrase, uid)
        @Suppress("UNUSED_VARIABLE") val ignored = current
    }
}
