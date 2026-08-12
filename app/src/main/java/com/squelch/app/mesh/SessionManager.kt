package com.squelch.app.mesh

import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.VaultSession
import com.squelch.app.crypto.noise.KeyPair
import com.squelch.app.crypto.noise.NoiseSession
import java.util.concurrent.ConcurrentHashMap

/**
 * One Noise XX session per peer. Initiator = peer with the larger edPub
 * (deadlock-free). On both sides the session produces two transport
 * keys (send + recv) and exposes encrypt()/decrypt() over them.
 */
class SessionManager(private val identity: Identity) {

    /** state per peer edPub hex string */
    private val sessions = ConcurrentHashMap<String, SessionState>()

    inner class SessionState(
        val peerEd: ByteArray,
        val initiator: Boolean
    ) {
        lateinit var session: NoiseSession
        var established = false
        var awaitingMsg2 = false
        var awaitingMsg3 = false
        var inFlightMsg1: ByteArray? = null

        fun start() {
            session = NoiseSession.createInitiator(
                pattern = com.squelch.app.crypto.noise.HandshakePattern.XX,
                localStatic = KeyPair(identity.xSecret, identity.xPub),
                remoteStatic = null
            )
            inFlightMsg1 = session.writeHandshake(Hello.encode(makeHello()))
        }

        fun makeHello(): com.squelch.app.mesh.Hello {
            val cs = Hello.callsignFor(identity.edPub, identity.xPub)
            return com.squelch.app.mesh.Hello(
                edPub = identity.edPub,
                xPub = identity.xPub,
                callsign = cs,
                capabilities = Hello.CAP_PLAIN or Hello.CAP_AES_GCM,
                deviceName = ""
            )
        }

        fun encrypt(plain: ByteArray): ByteArray =
            session.encrypt(plain)

        fun decrypt(ct: ByteArray): ByteArray =
            session.decrypt(ct)

        fun readHandshake(payload: ByteArray): ByteArray =
            session.readHandshake(payload)

        fun writeHandshake(payload: ByteArray = ByteArray(0)): ByteArray =
            session.writeHandshake(payload)
    }

    /** Get or create a session for [peerEd]. Initiates handshake if new. */
    fun ensureSessionFor(peerEd: ByteArray): SessionState {
        val hex = com.squelch.app.util.Bytes.hex(peerEd)
        sessions[hex]?.let { return it }
        val initiator = com.squelch.app.util.Bytes.compareUnsigned(identity.edPub, peerEd) > 0
        val st = SessionState(peerEd.copyOf(), initiator = initiator)
        st.start()
        sessions[hex] = st
        return st
    }

    fun sessionFor(peerEd: ByteArray): SessionState? =
        sessions[com.squelch.app.util.Bytes.hex(peerEd)]

    /** Drop a session (e.g. link closed). */
    fun drop(peerEd: ByteArray) {
        sessions.remove(com.squelch.app.util.Bytes.hex(peerEd))
    }

    /** Build the live Identity from the unlocked vault, or null if locked. */
    companion object {
        fun identityOrNull(): Identity? =
            VaultSession.mnemonicOrNull()?.let { Identity.fromMnemonic(it) }
    }
}
