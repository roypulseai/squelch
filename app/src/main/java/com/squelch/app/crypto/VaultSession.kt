package com.squelch.app.crypto

object VaultSession {

    @Volatile private var kDb: ByteArray? = null
    @Volatile private var googleUid: String? = null
    private val lock = Any()

    val isUnlocked: Boolean get() = synchronized(lock) { kDb != null }

    fun kDbOrEmpty(): ByteArray = synchronized(lock) { kDb?.copyOf() ?: ByteArray(0) }
    fun googleUidOrNull(): String? = synchronized(lock) { googleUid }

    fun unlock(kDb: ByteArray, googleUid: String) {
        synchronized(lock) {
            this.kDb = kDb.copyOf()
            this.googleUid = googleUid
        }
    }

    fun lock() {
        synchronized(lock) {
            kDb?.fill(0)
            kDb = null
            googleUid = null
        }
    }
}
