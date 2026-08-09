package com.squelch.app.crypto.noise

import java.security.SecureRandom

/**
 * A Noise session: runs an XX or KK handshake, then provides encrypt/decrypt
 * transport messages with periodic forward-secrecy rekeying.
 *
 * After the handshake completes: initiator uses c1 to send / c2 to receive,
 * responder uses c2 to send / c1 to receive (spec 5.3).
 */
class NoiseSession private constructor(
    private val pattern: HandshakePattern,
    private val initiator: Boolean,
    private val localStatic: KeyPair,
    private val remoteStatic: ByteArray?,
    private val random: SecureRandom = SecureRandom()
) {
    private var hs: HandshakeState? = null
    private var sendCs: CipherState? = null
    private var recvCs: CipherState? = null
    private var handshakeComplete = false
    private var transportCount = 0L

    companion object {
        private const val REKEY_EVERY = 1000

        fun createInitiator(
            pattern: HandshakePattern,
            localStatic: KeyPair,
            remoteStatic: ByteArray?,
            prologue: ByteArray = ByteArray(0)
        ): NoiseSession {
            val s = NoiseSession(pattern, true, localStatic, remoteStatic)
            s.hs = HandshakeState.initialize(pattern, true, prologue, localStatic, remoteStatic, s.random)
            return s
        }

        fun createResponder(
            pattern: HandshakePattern,
            localStatic: KeyPair,
            remoteStatic: ByteArray?,
            prologue: ByteArray = ByteArray(0)
        ): NoiseSession {
            val s = NoiseSession(pattern, false, localStatic, remoteStatic)
            s.hs = HandshakeState.initialize(pattern, false, prologue, localStatic, remoteStatic, s.random)
            return s
        }
    }

    val isHandshakeComplete: Boolean get() = handshakeComplete
    val isInitiator: Boolean get() = initiator
    val patternName: String get() = pattern.name

    /** Write the next handshake message; returns its wire bytes. */
    fun writeHandshake(payload: ByteArray = ByteArray(0)): ByteArray {
        val h = hs ?: throw IllegalStateException("no handshake in progress")
        val result = h.writeMessage(payload)
        if (result.done) complete(result.split!!)
        return result.message
    }

    /** Read a handshake message; returns the plaintext handshake payload. */
    fun readHandshake(message: ByteArray): ByteArray {
        val h = hs ?: throw IllegalStateException("no handshake in progress")
        val result = h.readMessage(message)
        if (result.done) complete(result.split!!)
        return result.payload
    }

    private fun complete(split: Pair<CipherState, CipherState>) {
        val (c1, c2) = split
        if (initiator) {
            sendCs = c1
            recvCs = c2
        } else {
            sendCs = c2
            recvCs = c1
        }
        handshakeComplete = true
        transportCount = 0
    }

    /** Encrypt a transport message (ad = empty, per spec). */
    fun encrypt(plaintext: ByteArray): ByteArray {
        require(handshakeComplete) { "handshake not complete" }
        if (++transportCount >= REKEY_EVERY) rekey()
        return sendCs!!.encryptWithAd(ByteArray(0), plaintext)
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {
        require(handshakeComplete) { "handshake not complete" }
        return recvCs!!.decryptWithAd(ByteArray(0), ciphertext)
    }

    /** Explicit rekey of both directions (spec 11.3). */
    fun rekey() {
        sendCs?.rekey()
        recvCs?.rekey()
        transportCount = 0
    }

    fun peerStatic(): ByteArray? = hs?.remoteStatic() ?: remoteStatic
}
