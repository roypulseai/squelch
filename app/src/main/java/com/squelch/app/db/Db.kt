package com.squelch.app.db

import com.squelch.app.crypto.VaultSession

/**
 * Thin facade over the SQLCipher-backed Room database that:
 *   1. Holds the AppDatabase singleton once a vault unlock populates
 *      VaultSession.kDb.
 *   2. Exposes synchronous DAOs for ad-hoc reads (used by the engine).
 *   3. Pushes write operations through a coroutine scope owned by
 *      SquelchApp.
 *
 * The DAO accessors throw if the DB hasn't been opened yet (i.e. the
 * vault is still locked). Callers wrap writes in a `require(db.isOpen)`
 * guard.
 */
object Db {
    @Volatile
    var instance: AppDatabase? = null

    fun isOpen(): Boolean = instance != null

    fun requireDb(): AppDatabase =
        instance ?: error("SQLCipher DB is not open - unlock the vault first")

    fun contacts() = requireDb().contacts()
    fun chatRooms() = requireDb().chatRooms()
    fun messages() = requireDb().messages()

    /** Re-export a write into a coroutine. Caller is responsible for the scope. */
    fun write(work: suspend (AppDatabase) -> Unit) {
        val db = requireDb()
        kotlinx.coroutines.runBlocking {
            work(db)
        }
    }
}
