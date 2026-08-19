package com.squelch.app.crypto

import com.squelch.app.crypto.noise.KeyPair
import com.squelch.app.util.Bytes
import java.security.MessageDigest

data class Identity(
    val edSeed: ByteArray,
    val xSecret: ByteArray
) {
    val edPub: ByteArray by lazy { Ed25519.publicKey(edSeed) }
    val xPub: ByteArray by lazy { X25519.publicKey(xSecret) }

    fun edKeyPair(): KeyPair = KeyPair(edSeed, edPub)
    fun xKeyPair(): KeyPair = KeyPair(xSecret, xPub)

    companion object {
        const val BLOB_SIZE = 64

        fun fromMnemonic(mnemonic: String): Identity {
            val seed64 = Bip39.mnemonicToSeed(mnemonic)
            require(seed64.size >= 64)
            val edSeed = seed64.copyOfRange(0, 32)
            val xSecret = seed64.copyOfRange(32, 64)
            return Identity(edSeed, xSecret)
        }

        fun fromGoogleUid(googleUid: String): Identity {
            val md = MessageDigest.getInstance("SHA-512")
            val seed64 = md.digest("squelch_identity_v1:$googleUid".toByteArray(Charsets.UTF_8))
            require(seed64.size >= 64)
            return Identity(
                edSeed = seed64.copyOfRange(0, 32),
                xSecret = seed64.copyOfRange(32, 64)
            )
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
