package com.squelch.app.crypto

object VaultSession {

    @Volatile private var kDb: ByteArray? = null
    @Volatile private var googleUid: String? = null

    val isUnlocked: Boolean get() = kDb != null

    fun kDbOrEmpty(): ByteArray = kDb?.copyOf() ?: ByteArray(0)
    fun googleUidOrNull(): String? = googleUid

    fun unlock(kDb: ByteArray, googleUid: String) {
        this.kDb = kDb.copyOf()
        this.googleUid = googleUid
    }

    fun lock() {
        kDb?.fill(0)
        kDb = null
        googleUid = null
    }
}
