package com.squelch.app.crypto.noise

import com.squelch.app.crypto.X25519
import com.squelch.app.util.Bytes
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

data class KeyPair(val secret: ByteArray, val public: ByteArray)

enum class HandshakePattern(val messages: List<List<String>>) {
    XX(listOf(listOf("e"), listOf("e", "ee", "s", "es"), listOf("s", "se"))),
    KK(listOf(listOf("e", "es", "ss"), listOf("e", "ee", "se")));

    val patternName: String get() = when (this) {
        XX -> "XX"
        KK -> "KK"
    }
}

class HandshakeState private constructor(
    private val pattern: HandshakePattern,
    private val initiator: Boolean,
    private val localStatic: KeyPair,
    private var remoteStatic: ByteArray?,
    private val symmetric: SymmetricState,
    private val random: SecureRandom,
    private var e: KeyPair? = null,
    private var re: ByteArray? = null
) {
    private var messageIndex = 0

    class Result(
        val message: ByteArray,
        val payload: ByteArray,
        val done: Boolean,
        val split: Pair<CipherState, CipherState>?
    )

    companion object {
        fun initialize(
            pattern: HandshakePattern,
            initiator: Boolean,
            prologue: ByteArray,
            localStatic: KeyPair,
            remoteStatic: ByteArray?,
            random: SecureRandom
        ): HandshakeState {
            require(remoteStatic != null || pattern == HandshakePattern.XX) {
                "remote static key required for ${pattern.name}"
            }
            val protocolName = "Noise_${pattern.name}_25519_AESGCM_SHA256".toByteArray(Charsets.UTF_8)
            val symmetric = SymmetricState()
            symmetric.initializeSymmetric(protocolName)
            symmetric.mixHash(prologue)

            val state = HandshakeState(pattern, initiator, localStatic, remoteStatic, symmetric, random)

            if (pattern == HandshakePattern.KK) {
                val initiatorStaticPub = if (initiator) localStatic.public else remoteStatic!!
                val responderStaticPub = if (initiator) remoteStatic!! else localStatic.public
                symmetric.mixHash(initiatorStaticPub)
                symmetric.mixHash(responderStaticPub)
            }
            return state
        }
    }

    private val isDone get() = messageIndex == pattern.messages.size

    fun writeMessage(payload: ByteArray): Result {
        require(!isDone) { "handshake already complete" }
        val tokens = pattern.messages[messageIndex]
        messageIndex++
        val buf = ByteArrayOutputStream()

        for (token in tokens) {
            when (token) {
                "e" -> {
                    val (secret, public) = X25519.keyPair(random)
                    e = KeyPair(secret, public)
                    buf.write(public)
                    symmetric.mixHash(public)
                }
                "s" -> buf.write(symmetric.encryptAndHash(localStatic.public))
                "ee" -> symmetric.mixKey(X25519.dh(e!!.secret, re!!))
                "es" -> if (initiator) {
                    symmetric.mixKey(X25519.dh(e!!.secret, remoteStatic!!))
                } else {
                    symmetric.mixKey(X25519.dh(localStatic.secret, re!!))
                }
                "se" -> if (initiator) {
                    symmetric.mixKey(X25519.dh(localStatic.secret, re!!))
                } else {
                    symmetric.mixKey(X25519.dh(e!!.secret, remoteStatic!!))
                }
                "ss" -> symmetric.mixKey(X25519.dh(localStatic.secret, remoteStatic!!))
            }
        }

        buf.write(symmetric.encryptAndHash(payload))
        return Result(buf.toByteArray(), ByteArray(0), isDone, if (isDone) symmetric.split() else null)
    }

    fun readMessage(message: ByteArray): Result {
        require(!isDone) { "handshake already complete" }
        val tokens = pattern.messages[messageIndex]
        messageIndex++
        var pos = 0

        for (token in tokens) {
            when (token) {
                "e" -> {
                    val pub = message.copyOfRange(pos, pos + X25519.KEY_SIZE)
                    pos += X25519.KEY_SIZE
                    re = pub
                    symmetric.mixHash(pub)
                }
                "s" -> {
                    val len = if (symmetric.hasKey()) X25519.KEY_SIZE + 16 else X25519.KEY_SIZE
                    val field = message.copyOfRange(pos, pos + len)
                    pos += len
                    val rs = symmetric.decryptAndHash(field)
                    if (remoteStatic != null) {
                        require(Bytes.constantTimeEquals(remoteStatic!!, rs)) { "static key mismatch" }
                    }
                    remoteStatic = rs
                }
                "ee" -> symmetric.mixKey(X25519.dh(e!!.secret, re!!))
                "es" -> if (initiator) {
                    symmetric.mixKey(X25519.dh(e!!.secret, remoteStatic!!))
                } else {
                    symmetric.mixKey(X25519.dh(localStatic.secret, re!!))
                }
                "se" -> if (initiator) {
                    symmetric.mixKey(X25519.dh(localStatic.secret, re!!))
                } else {
                    symmetric.mixKey(X25519.dh(e!!.secret, remoteStatic!!))
                }
                "ss" -> symmetric.mixKey(X25519.dh(localStatic.secret, remoteStatic!!))
            }
        }

        val payload = symmetric.decryptAndHash(message.copyOfRange(pos, message.size))
        return Result(message, payload, isDone, if (isDone) symmetric.split() else null)
    }

    fun remoteStatic(): ByteArray? = remoteStatic

    fun handshakeHash(): ByteArray = symmetric.getHandshakeHash()
}
