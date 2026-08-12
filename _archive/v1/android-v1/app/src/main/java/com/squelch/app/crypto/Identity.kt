package com.squelch.app.crypto

import com.squelch.app.crypto.noise.Hkdf

/** The device identity: an Ed25519 signing keypair plus an X25519 key-exchange keypair. */
data class Identity(
    val edSeed: ByteArray,
    val xSecret: ByteArray
) {
    val edPub: ByteArray by lazy { Ed25519.publicKey(edSeed) }
    val xPub: ByteArray by lazy { X25519.publicKey(xSecret) }

    /** Fingerprint = SHA-256(edPub || xPub); used for call-sign derivation and display. */
    val fingerprint: ByteArray by lazy { Hkdf.hash(com.squelch.app.util.Bytes.concat(edPub, xPub)) }

    fun toBlob(): ByteArray = com.squelch.app.util.Bytes.concat(edSeed, xSecret)

    companion object {
        const val BLOB_SIZE = 64

        fun fromBlob(blob: ByteArray): Identity {
            require(blob.size == BLOB_SIZE)
            return Identity(
                blob.copyOfRange(0, 32),
                blob.copyOfRange(32, 64)
            )
        }
    }
}
