package com.squelch.app.mesh

import com.squelch.app.crypto.noise.HandshakePattern
import com.squelch.app.crypto.noise.KeyPair
import com.squelch.app.crypto.noise.NoiseSession
import com.squelch.app.util.Bytes
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages end-to-end Noise sessions, established *through* the mesh itself:
 * handshake envelopes are routed (and store-and-forward cached) exactly like
 * chat messages (spec 2.2 / 5.2). Sessions are per-peer E2E — a relay hop only
 * ever forwards ciphertext and cannot read it.
 *
 * Role assignment: the peer with the larger Ed25519 pubkey initiates (deadlock-free).
 * XX is the default pattern (mutual auth without prior trust); KK exists in the
 * Noise layer for NFC pre-shared statics.
 */
class SessionManager(private val engine: MeshEngine) {

    class SessionState(val peerEd: ByteArray, val initiator: Boolean) {
        lateinit var session: NoiseSession
        var awaitingMsg2 = false
        var awaitingMsg3 = false
        var established = false
        var nfcNonce: ByteArray? = null
        val outbox = ArrayDeque<InnerMessage>()
    }

    private val sessions = ConcurrentHashMap<String, SessionState>()

    /** Send the next handshake message toward the peer. */
    fun createInitiatorSession(peer: Peer, nfcNonce: ByteArray? = null): SessionState {
        sessions[peer.edHex]?.let {
            it.nfcNonce = it.nfcNonce ?: nfcNonce
            return it
        }
        val ss = SessionState(peer.edPub, initiator = true)
        ss.nfcNonce = nfcNonce
        ss.session = NoiseSession.createInitiator(
            HandshakePattern.XX,
            KeyPair(engine.identity.xSecret, engine.identity.xPub),
            null
        )
        sessions[peer.edHex] = ss
        sendNext(peer, ss)
        return ss
    }

    fun getSession(peerEdHex: String): SessionState? = sessions[peerEdHex]

    /** Queue an inner message to be sent once the session is established. */
    fun queueForPeer(peer: Peer, inner: InnerMessage) {
        val ss = getSession(peer.edHex)
        if (ss == null) {
            createInitiatorSession(peer).outbox.addLast(inner)
        } else if (ss.established) {
            engine.sendEncryptedDm(peer, inner)
        } else {
            ss.outbox.addLast(inner)
        }
    }

    /** An HS envelope addressed to me arrived. */
    fun onHandshake(packet: MeshPacket, envelope: MeshEnvelope) {
        val senderHex = Bytes.hex(packet.senderPk)
        val existing = sessions[senderHex]
        if (existing == null) {
            // They initiated. Only valid if we have their pubkey context; accept XX from anyone.
            val ss = SessionState(packet.senderPk.copyOf(), initiator = false)
            ss.session = NoiseSession.createResponder(
                HandshakePattern.XX,
                KeyPair(engine.identity.xSecret, engine.identity.xPub),
                null
            )
            sessions[senderHex] = ss
            respond(ss, packet, envelope)
        } else if (existing.initiator) {
            if (existing.awaitingMsg2) {
                existing.awaitingMsg2 = false
                val hello = existing.session.readHandshake(envelope.ciphertext)
                engine.onPeerHandshakeHello(existing.peerEd, hello)
                val msg3 = existing.session.writeHandshake(existing.nfcNonce ?: ByteArray(0))
                existing.established = true
                engine.sendHandshake(packet.senderPk, msg3)
                established(existing)
            }
        } else {
            if (existing.awaitingMsg3) {
                existing.awaitingMsg3 = false
                val payload = existing.session.readHandshake(envelope.ciphertext)
                existing.established = true
                if (payload.isNotEmpty() && payload.size == 16) {
                    engine.onNfcNonceReturned(packet.senderPk, payload)
                }
                established(existing)
            }
        }
    }

    private fun respond(ss: SessionState, packet: MeshPacket, envelope: MeshEnvelope) {
        try {
            val initiatorHello = ss.session.readHandshake(envelope.ciphertext)
            engine.onPeerHandshakeHello(ss.peerEd, initiatorHello)
            val msg2 = ss.session.writeHandshake(engine.myHello())
            ss.awaitingMsg3 = true
            engine.sendHandshake(packet.senderPk, msg2)
        } catch (e: Exception) {
            engine.onSessionError(ss.peerEd)
            sessions.remove(Bytes.hex(ss.peerEd))
        }
    }

    private fun sendNext(peer: Peer, ss: SessionState) {
        try {
            val msg1 = ss.session.writeHandshake(engine.myHello())
            ss.awaitingMsg2 = true
            engine.sendHandshake(peer.edPub, msg1)
        } catch (e: Exception) {
            engine.onSessionError(peer.edPub)
            sessions.remove(peer.edHex)
        }
    }

    private fun established(ss: SessionState) {
        val peer = engine.peers.get(ss.peerEd) ?: return
        peer.mutualStatics = true
        engine.onSessionEstablished(peer)
        while (ss.outbox.isNotEmpty()) {
            engine.sendEncryptedDm(peer, ss.outbox.removeFirst())
        }
    }

    fun encryptDm(peer: Peer, inner: ByteArray): ByteArray? {
        val ss = sessions[peer.edHex] ?: return null
        if (!ss.established) return null
        return try {
            ss.session.encrypt(inner)
        } catch (e: Exception) {
            null
        }
    }

    fun decryptDm(senderEd: ByteArray, ciphertext: ByteArray): ByteArray? {
        val ss = sessions[Bytes.hex(senderEd)] ?: return null
        if (!ss.established) return null
        return try {
            ss.session.decrypt(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

    fun rekeyAll() {
        sessions.values.forEach { if (it.established) it.session.rekey() }
    }

    fun dropPeer(edHex: String) {
        sessions.remove(edHex)
    }

    fun dropAll() = sessions.clear()
}
