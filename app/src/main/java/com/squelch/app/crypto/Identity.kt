package com.squelch.app.crypto

import com.squelch.app.crypto.noise.KeyPair
import com.squelch.app.util.Bytes

/**
 * The device identity: an Ed25519 signing keypair plus an X25519
 * key-exchange keypair, both deterministically derived from the BIP-39
 * mnemonic that lives inside the encrypted vault.
 *
 *     seed64 = BIP-39.mnemonicToSeed(mnemonic, "")
 *     edSeed = seed64.take(32)
 *     xSecret = seed64.drop(32).take(32)
 *
 * The public keys are derived lazily; the secrets live in this object
 * only while the vault is unlocked (the call site passes the mnemonic
 * in and forgets about it).
 */
data class Identity(
    val edSeed: ByteArray,
    val xSecret: ByteArray
) {
    val edPub: ByteArray by lazy { Ed25519.publicKey(edSeed) }
    val xPub: ByteArray by lazy { X25519.publicKey(xSecret) }

    fun edKeyPair(): KeyPair = KeyPair(xSecret, xPub) // placeholder reuse; not used directly

    companion object {
        const val BLOB_SIZE = 64

        fun fromMnemonic(mnemonic: String): Identity {
            val seed64 = Bip39.mnemonicToSeed(mnemonic)
            require(seed64.size >= 64)
            val edSeed = seed64.copyOfRange(0, 32)
            val xSecret = seed64.copyOfRange(32, 64)
            return Identity(edSeed, xSecret)
        }

        fun fromBlob(blob: ByteArray): Identity {
            require(blob.size >= BLOB_SIZE)
            return Identity(
                edSeed = blob.copyOfRange(0, 32),
                xSecret = blob.copyOfRange(32, BLOB_SIZE)
            )
        }

        fun toBlob(id: Identity): ByteArray = Bytes.concat(id.edSeed, id.xSecret)
    }
}
