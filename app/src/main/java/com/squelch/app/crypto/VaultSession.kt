package com.squelch.app.crypto

object VaultSession {

    @Volatile private var mnemonic: String? = null
    @Volatile private var kDb: ByteArray? = null
    @Volatile private var googleUid: String? = null

    val isUnlocked: Boolean get() = mnemonic != null && kDb != null

    fun mnemonicOrNull(): String? = mnemonic
    fun kDbOrEmpty(): ByteArray = kDb?.copyOf() ?: ByteArray(0)
    fun googleUidOrNull(): String? = googleUid

    fun unlock(mnemonic: String, kDb: ByteArray, googleUid: String) {
        this.mnemonic = mnemonic
        this.kDb = kDb.copyOf()
        this.googleUid = googleUid
    }

    fun lock() {
        mnemonic = null
        kDb?.fill(0)
        kDb = null
        googleUid = null
    }
}
