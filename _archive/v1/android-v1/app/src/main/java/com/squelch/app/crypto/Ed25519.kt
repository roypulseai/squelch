package com.squelch.app.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/** Ed25519 signing wrapper (BouncyCastle). Identity = 32-byte seed. */
object Ed25519 {
    const val SEED_SIZE = 32
    const val PUBLIC_SIZE = 32
    const val SIGNATURE_SIZE = 64

    fun generateSeed(random: SecureRandom): ByteArray {
        val seed = ByteArray(SEED_SIZE)
        random.nextBytes(seed)
        return seed
    }

    fun publicKey(seed: ByteArray): ByteArray {
        require(seed.size == SEED_SIZE)
        return Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
    }

    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(seed, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != PUBLIC_SIZE || signature.size != SIGNATURE_SIZE) return false
        return try {
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            signer.update(message, 0, message.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }
}
