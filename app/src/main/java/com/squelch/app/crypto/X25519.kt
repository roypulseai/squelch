package com.squelch.app.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

object X25519 {
    const val KEY_SIZE = 32

    fun generateSecret(random: SecureRandom): ByteArray {
        val secret = ByteArray(KEY_SIZE)
        random.nextBytes(secret)
        return secret
    }

    fun publicKey(secret: ByteArray): ByteArray {
        require(secret.size == KEY_SIZE)
        return X25519PrivateKeyParameters(secret, 0).generatePublicKey().encoded
    }

    fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_SIZE && publicKey.size == KEY_SIZE)
        val out = ByteArray(KEY_SIZE)
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        agreement.calculateAgreement(X25519PublicKeyParameters(publicKey, 0), out, 0)
        return out
    }

    fun keyPair(random: SecureRandom): Pair<ByteArray, ByteArray> {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(random))
        val pair = gen.generateKeyPair()
        val priv = pair.private as X25519PrivateKeyParameters
        val pub = pair.public as X25519PublicKeyParameters
        return priv.encoded to pub.encoded
    }
}
