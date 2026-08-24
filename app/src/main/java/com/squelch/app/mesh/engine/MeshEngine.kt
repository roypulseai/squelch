package com.squelch.app.mesh.engine

import android.util.Log
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.noise.HandshakePattern
import com.squelch.app.crypto.noise.NoiseSession
import com.squelch.app.mesh.protocol.MessageCodec
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.util.Bytes
import com.squelch.app.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MeshEngine(
    private val identity: Identity,
    private val transports: List<Transport>
) {
    companion object {
        private const val TAG = "MeshEngine"
        private const val SEEN_SET_MAX = 2000
        private const val PRESENCE_BROADCAST_INTERVAL_MS = 30_000L
        private const val PEER_TIMEOUT_MS = 60_000L
    }

    private var scope: CoroutineScope? = null
    private val sessions = ConcurrentHashMap<String, NoiseSession>()
    private val rng = SecureRandom()
    @Volatile private var isRunning = false
    val running: Boolean get() = isRunning
    val selfPubHex: String get() = identity.edPub.toHex()

    private val _peers = MutableStateFlow<Set<String>>(emptySet())
    val peers: StateFlow<Set<String>> = _peers.asStateFlow()

    private val _onlinePeers = MutableStateFlow<Set<String>>(emptySet())
    val onlinePeers: StateFlow<Set<String>> = _onlinePeers.asStateFlow()

    private val _messages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<IncomingMessage> = _messages.asSharedFlow()

    // Duplicate detection
    private val seenMessages = object : LinkedHashMap<String, Long>(SEEN_SET_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > SEEN_SET_MAX
        }
    }

    // Presence tracking
    private val peerLastSeen = ConcurrentHashMap<String, Long>()

    data class IncomingMessage(
        val senderEdPubHex: String,
        val plaintext: ByteArray,
        val timestamp: Long,
        val hopCount: Int = 0,
        val originalSender: String = ""
    )

    fun start() {
        if (isRunning) return
        isRunning = true
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        for (transport in transports) {
            try {
                transport.start()
            } catch (e: Exception) {
                Log.e(TAG, "Transport ${transport.name} failed to start: ${e.message}")
                continue
            }
            s.launch {
                try {
                    transport.incoming.collect { frame ->
                        handleFrame(frame)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Transport ${transport.name} collection failed: ${e.message}")
                }
            }
        }
        // Periodic presence broadcast
        s.launch {
            while (isRunning) {
                delay(PRESENCE_BROADCAST_INTERVAL_MS)
                broadcastPresence()
                pruneStalePeers()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        for (transport in transports) {
            try { transport.stop() } catch (_: Exception) {}
        }
        sessions.clear()
        peerLastSeen.clear()
        scope?.cancel()
        scope = null
    }

    fun sendMessage(recipientEdPubHex: String, plaintext: ByteArray) {
        val session = sessions[recipientEdPubHex]
        val ciphertext: ByteArray
        val kind: Int
        if (session != null && session.isHandshakeComplete) {
            ciphertext = session.encrypt(plaintext)
            kind = Transport.TransportFrame.KIND_DATA
        } else {
            ciphertext = plaintext
            kind = Transport.TransportFrame.KIND_HS
        }
        val msg = MessageCodec.MeshMessage(
            sender = identity.edPub.toHex(),
            recipient = recipientEdPubHex,
            msgId = UUID.randomUUID().toString(),
            ciphertext = ciphertext,
            timestamp = System.currentTimeMillis(),
            ttl = 7,
            hopCount = 0
        )
        val encoded = MessageCodec.encode(msg)
        for (transport in transports) {
            try {
                transport.send(recipientEdPubHex, encoded)
            } catch (e: Exception) {
                Log.e(TAG, "Send via ${transport.name} failed: ${e.message}")
            }
        }
    }

    private fun handleFrame(frame: Transport.TransportFrame) {
        val sender = frame.senderEdPubHex
        if (sender == identity.edPub.toHex()) return

        _peers.value = _peers.value + sender
        peerLastSeen[sender] = System.currentTimeMillis()
        _onlinePeers.value = _onlinePeers.value + sender

        when (frame.kind) {
            Transport.TransportFrame.KIND_HELLO -> {
                Log.d(TAG, "HELLO from $sender")
                try { initiateHandshake(sender) } catch (e: Exception) {
                    Log.e(TAG, "Handshake initiation failed: ${e.message}")
                }
            }
            Transport.TransportFrame.KIND_HS -> {
                try { handleHandshake(sender, frame.payload) } catch (e: Exception) {
                    Log.e(TAG, "Handshake handling failed: ${e.message}")
                }
            }
            Transport.TransportFrame.KIND_DATA -> {
                try { handleData(sender, frame.payload) } catch (e: Exception) {
                    Log.e(TAG, "Data handling failed: ${e.message}")
                }
            }
            Transport.TransportFrame.KIND_TYPING -> {
                try { handleTyping(sender, frame.payload) } catch (e: Exception) {
                    Log.e(TAG, "Typing handling failed: ${e.message}")
                }
            }
            Transport.TransportFrame.KIND_PRESENCE -> {
                handlePresenceUpdate(sender)
            }
        }
    }

    private fun initiateHandshake(remotePubHex: String) {
        val remotePub = Bytes.unhex(remotePubHex)
        val session = NoiseSession.createInitiator(
            pattern = HandshakePattern.XX,
            localStatic = identity.edKeyPair(),
            remoteStatic = remotePub,
            prologue = ByteArray(0)
        )
        sessions[remotePubHex] = session
        val hsMsg = session.writeHandshake()
        val wrapped = wrapHandshakeMessage(remotePubHex, hsMsg)
        for (transport in transports) {
            try { transport.send(remotePubHex, wrapped, Transport.TransportFrame.KIND_HS) } catch (_: Exception) {}
        }
    }

    private fun handleHandshake(sender: String, payload: ByteArray) {
        val unwrapped = unwrapHandshakeMessage(payload) ?: return
        val session = sessions.getOrPut(sender) {
            val remotePub = Bytes.unhex(sender)
            NoiseSession.createResponder(
                pattern = HandshakePattern.XX,
                localStatic = identity.edKeyPair(),
                remoteStatic = remotePub,
                prologue = ByteArray(0)
            )
        }
        if (session.isHandshakeComplete) return
        try {
            session.readHandshake(unwrapped)
            if (!session.isHandshakeComplete) {
                val response = session.writeHandshake()
                val wrapped = wrapHandshakeMessage(sender, response)
                for (transport in transports) {
                    try { transport.send(sender, wrapped, Transport.TransportFrame.KIND_HS) } catch (_: Exception) {}
                }
            }
            Log.d(TAG, "Handshake complete with $sender")
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed with $sender: ${e.message}")
            sessions.remove(sender)
        }
    }

    private fun handleData(sender: String, payload: ByteArray) {
        val session = sessions[sender]
        if (session == null || !session.isHandshakeComplete) {
            Log.w(TAG, "No session for $sender, initiating handshake")
            initiateHandshake(sender)
            return
        }
        try {
            val plaintext = session.decrypt(payload)
            val msg = MessageCodec.decode(plaintext) ?: return

            // Duplicate detection
            synchronized(seenMessages) {
                if (seenMessages.containsKey(msg.msgId)) return
                seenMessages[msg.msgId] = System.currentTimeMillis()
            }

            // If this message is for us, deliver to app
            if (msg.recipient == identity.edPub.toHex()) {
                scope?.launch {
                    _messages.emit(
                        IncomingMessage(
                            senderEdPubHex = msg.sender,
                            plaintext = msg.ciphertext,
                            timestamp = msg.timestamp,
                            hopCount = msg.hopCount,
                            originalSender = msg.originalSender
                        )
                    )
                }
            }

            // Multi-hop relay: forward if TTL allows
            if (msg.ttl > msg.hopCount + 1 && msg.recipient != identity.edPub.toHex()) {
                val relayed = msg.copy(
                    sender = identity.edPub.toHex(),
                    hopCount = msg.hopCount + 1
                )
                val relayedEncoded = MessageCodec.encode(relayed)
                Log.d(TAG, "Relaying message ${msg.msgId.take(8)} hop=${msg.hopCount + 1} ttl=${msg.ttl}")
                for (transport in transports) {
                    try {
                        transport.send(msg.recipient, relayedEncoded)
                    } catch (e: Exception) {
                        Log.e(TAG, "Relay via ${transport.name} failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed from $sender: ${e.message}")
        }
    }

    private fun handleTyping(sender: String, payload: ByteArray) {
        scope?.launch {
            _messages.emit(
                IncomingMessage(
                    senderEdPubHex = sender,
                    plaintext = payload,
                    timestamp = System.currentTimeMillis(),
                    originalSender = sender
                )
            )
        }
    }

    private fun handlePresenceUpdate(sender: String) {
        peerLastSeen[sender] = System.currentTimeMillis()
        _onlinePeers.value = _onlinePeers.value + sender
    }

    private fun broadcastPresence() {
        // Presence is tracked passively via transport discovery (BLE scan/advertise).
        // No need to send a broadcast frame — it would just queue into pendingMessages[""]
        // and grow unboundedly since no device maps to empty recipient.
    }

    private fun pruneStalePeers() {
        val now = System.currentTimeMillis()
        val stalePeers = peerLastSeen.entries
            .filter { now - it.value > PEER_TIMEOUT_MS }
            .map { it.key }
        if (stalePeers.isNotEmpty()) {
            val staleSet = stalePeers.toSet()
            val currentOnline = _onlinePeers.value.toMutableSet()
            currentOnline.removeAll(staleSet)
            _onlinePeers.value = currentOnline

            val currentPeers = _peers.value.toMutableSet()
            currentPeers.removeAll(staleSet)
            _peers.value = currentPeers

            for (peer in stalePeers) {
                peerLastSeen.remove(peer)
                sessions.remove(peer)
            }
        }
    }

    private fun wrapHandshakeMessage(remotePubHex: String, hsMsg: ByteArray): ByteArray {
        val json = org.json.JSONObject().apply {
            put("s", identity.edPub.toHex())
            put("r", remotePubHex)
            put("hs", java.util.Base64.getEncoder().encodeToString(hsMsg))
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun unwrapHandshakeMessage(data: ByteArray): ByteArray? {
        return try {
            val json = org.json.JSONObject(String(data, Charsets.UTF_8))
            val hs = json.optString("hs", "")
            if (hs.isNotEmpty()) java.util.Base64.getDecoder().decode(hs) else null
        } catch (e: Exception) {
            null
        }
    }
}
