package com.squelch.app.mesh

import android.content.Context
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.VaultSession
import com.squelch.app.crypto.AesGcm
import com.squelch.app.crypto.noise.Hkdf
import com.squelch.app.db.Db
import com.squelch.app.mesh.online.RelayTransport
import com.squelch.app.util.Bytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Coordinator that:
 *   - Owns the [AndroidMeshManager] (Nearby mesh).
 *   - Performs Noise XX handshakes with every peer that connects.
 *   - Signs + encrypts + sends DMs and room messages on outgoing.
 *   - Decodes + verifies + decrypts incoming frames.
 *   - Persists contact records + messages to the SQLCipher DB.
 *
 * The engine is unlocked only while the vault is. Sign in + PIN unlocks
 * the mnemonic; the Identity is derived from that once.
 */
class MeshEngine(context: Context) {

    private val manager = AndroidMeshManager(context)

    private val relay = RelayTransport(context,
        identity = { identity },
        relayUrl = RelayTransport.DEFAULT_RELAY_URL)

    private val _relayStatus = MutableStateFlow(relay.status.value)
    val relayStatus get() = relay.status

    private val _status = MutableStateFlow(MeshStatus())
    val status: StateFlow<MeshStatus> = _status.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, MeshPeer>>(emptyMap())
    val peers: StateFlow<Map<String, MeshPeer>> = _peers.asStateFlow()

    private val _messages = MutableStateFlow<List<SignedMessage>>(emptyList())
    val messages: StateFlow<List<SignedMessage>> = _messages.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var identity: Identity? = null
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, SessionManager.SessionState>()

    /** When the vault gets unlocked we begin handshakes with every
     *  already-connected peer. */
    init {
        rebuildIdentityIfPossible()
    }

    fun rebuildIdentityIfPossible() {
        val mn = VaultSession.mnemonicOrNull() ?: return
        identity = Identity.fromMnemonic(mn)
        // Issue link-layer HELLO so peers know our public keys.
        manager.start()
        manager.setListener(listener)
    }

    fun start() {
        if (_status.value.running) return
        rebuildIdentityIfPossible()
        manager.setListener(listener)
        manager.start()
        _status.value = _status.value.copy(
            running = true,
            lastError = null,
            startedAt = System.currentTimeMillis()
        )
        // Online relay comes up separately via startRelay(signed).
    }

    /** Bring up the online relay transport. Idempotent; safe to call
     *  after the user is signed in and the vault is unlocked. */
    fun startRelay(signed: com.squelch.app.auth.AuthState.SignedIn) {
        relay.start(signed) { kind, payload -> handleInboundFrame(kind, payload) }
    }

    fun stop() {
        if (!_status.value.running) return
        manager.stop()
        relay.stop()
        _status.value = _status.value.copy(running = false)
        _peers.value = emptyMap()
        sessions.clear()
    }

    /** Handle a single inbound frame regardless of which transport
     *  delivered it. Called from the Nearby listener and from the
     *  relay's WebSocket callback. */
    private fun handleInboundFrame(kind: Byte, payload: ByteArray) {
        // Hand off to the same routing the Nearby listener uses.
        when (kind) {
            KIND_HELLO -> handleHello("relay", payload)
            KIND_HANDSHAKE -> handleHandshake("relay", payload)
            KIND_DATA -> handleData("relay", payload)
        }
    }

    /** Compose an OuterMessage + encrypt with [peerEd]'s Noise session
     *  + sign packet + broadcast via AndroidMeshManager. */
    fun sendChat(peerEd: ByteArray, text: String) {
        val id = identity ?: return
        val session = openOrInitiateSession(peerEd)
        val msg = InnerMessage.chat(System.currentTimeMillis(),
            Bytes.randomId(java.security.SecureRandom()), text)
        val payload = if (session.established) session.encrypt(msg.encode()) else msg.encode()
        val packet = MeshPacket.sign(
            msgId = msg.msgId,
            ttl = 6,
            senderEdSeed = id.edSeed,
            senderEdPub = id.edPub,
            payload = payload
        )
        manager.broadcast(KIND_DATA, packet.encode())
        scope.launch { persistOutgoing(peerEd, msg, packet) }
        relay.send(KIND_DATA, packet.encode())
    }

    fun sendRoom(room: ByteArray, text: String) {
        val id = identity ?: return
        val msg = InnerMessage.roomMsg(System.currentTimeMillis(),
            Bytes.randomId(java.security.SecureRandom()), text)
        val ciphertext = com.squelch.app.crypto.AesGcm.encrypt(roomKey(room), msg.encode())
        val envelope = MeshEnvelope(MeshEnvelope.KIND_ROOM, room, ciphertext)
        val packet = MeshPacket.sign(
            msgId = msg.msgId,
            ttl = 6,
            senderEdSeed = id.edSeed,
            senderEdPub = id.edPub,
            payload = envelope.encode()
        )
        manager.broadcast(KIND_DATA, packet.encode())
    }

    private fun roomKey(room: ByteArray): ByteArray =
        Hkdf.hash(
            Bytes.concat("squelch-room:v1".toByteArray(), room)
        ).copyOf(32)

    private fun openOrInitiateSession(peerEd: ByteArray): SessionManager.SessionState {
        val id = identity ?: error("identity not initialised (vault must be unlocked)")
        val sm = SessionManager(id)
        val st = sm.ensureSessionFor(peerEd)
        sessions[Bytes.hex(peerEd)] = st
        if (!st.initiator) {
            // We are the responder -> wait for the initiator's msg1.
            return st
        }
        if (!st.established) {
            // Broadcast our msg1 (initiator side).
            val msg1 = st.inFlightMsg1 ?: st.writeHandshake()
            st.inFlightMsg1 = msg1
            manager.broadcast(KIND_HANDSHAKE, msg1)
        }
        return st
    }

    private val listener = object : AndroidMeshManager.Listener {
        override fun onFrame(endpointId: String, kind: Byte, payload: ByteArray) {
            when (kind) {
                KIND_HELLO -> handleHello(endpointId, payload)
                KIND_HANDSHAKE -> handleHandshake(endpointId, payload)
                KIND_DATA -> handleData(endpointId, payload)
            }
        }

        override fun onEndpointConnected(endpointId: String, info: com.google.android.gms.nearby.connection.ConnectionInfo) {
            _peers.value = _peers.value.toMutableMap().apply {
                put(endpointId, MeshPeer(endpointId, info.endpointName, System.currentTimeMillis()))
            }
            publish()
            // Initialise handshake with the new peer (initiator role).
            identity?.let { _ ->
                // peer xPub is unknown until HELLO; defer to handleHello.
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _peers.value = _peers.value.toMutableMap().apply { remove(endpointId) }
            publish()
        }

        override fun onError(message: String) {
            _status.value = _status.value.copy(lastError = message)
        }
    }

    private fun handleHello(endpointId: String, bytes: ByteArray) {
        val hello = try { Hello.decode(bytes) } catch (e: Exception) { return }
        // Persist contact if DB is open.
        if (Db.isOpen()) {
            scope.launch {
                Db.contacts().upsert(
                    com.squelch.app.db.ContactEntity(
                        pubkey = Bytes.hex(hello.edPub),
                        xPub = Bytes.hex(hello.xPub),
                        callsign = hello.callsign,
                        trustLevel = com.squelch.app.mesh.TrustLevel.MET,
                        capabilities = hello.capabilities,
                        lastSeen = System.currentTimeMillis(),
                        bluetoothAddress = "",
                        mutualStatics = false,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        // Kick off our Noise handshake with this peer.
        scope.launch { openAndHandshake(hello.edPub) }
    }

    private suspend fun openAndHandshake(peerEd: ByteArray) {
        val id = identity ?: return
        val sm = SessionManager(id)
        val st = sm.ensureSessionFor(peerEd)
        sessions[Bytes.hex(peerEd)] = st
        if (st.initiator && !st.established) {
            val msg1 = st.inFlightMsg1 ?: st.writeHandshake()
            st.inFlightMsg1 = msg1
            manager.broadcast(KIND_HANDSHAKE, msg1)
        }
    }

    private fun handleHandshake(endpointId: String, payload: ByteArray) {
        scope.launch {
            // v0.7 does not yet match the right peer's session by
            // endpointId (the wire ID  -  edPub mapping happens later via
            // message routing). For the initial round-trip we treat it as
            // a single in-flight session, which suffices for v0.7.
            val st = sessions.values.firstOrNull() ?: return@launch
            if (st.established) return@launch
            if (st.initiator) {
                if (!st.awaitingMsg2) return@launch
                try {
                    val peerHello = st.readHandshake(payload)
                    val msg3 = st.writeHandshake(
                        Hello.encode(
                            com.squelch.app.mesh.Hello(
                                edPub = identity!!.edPub,
                                xPub = identity!!.xPub,
                                callsign = Hello.callsignFor(identity!!.edPub, identity!!.xPub),
                                capabilities = Hello.CAP_PLAIN or Hello.CAP_AES_GCM,
                                deviceName = ""
                            )
                        )
                    )
                    manager.broadcast(KIND_HANDSHAKE, msg3)
                    st.established = true
                    _status.value = _status.value.copy(lastError = null)
                } catch (e: Exception) {
                    _status.value = _status.value.copy(lastError = "hs msg2: ${e.message}")
                }
            } else {
                // Responder path: msg1 was accepted in ensureSessionFor;
                // this is msg3.
                if (!st.awaitingMsg3) return@launch
                try {
                    st.readHandshake(payload)
                    st.established = true
                    _status.value = _status.value.copy(lastError = null)
                } catch (e: Exception) {
                    _status.value = _status.value.copy(lastError = "hs msg3: ${e.message}")
                }
            }
        }
    }

    private fun handleData(endpointId: String, payload: ByteArray) {
        val packet = try { MeshPacket.decode(payload) } catch (e: Exception) { return }
        if (!packet.verifySignature()) return
        if (packet.ttl <= 0) return
        scope.launch {
            val envelope = try { MeshEnvelope.decode(packet.payload) } catch (e: Exception) { return@launch }
            when (envelope.kind) {
                MeshEnvelope.KIND_HS -> Unit // handled in handleHandshake
                MeshEnvelope.KIND_DM -> {
                    val id = identity ?: return@launch
                    if (!envelope.addressedTo(id.edPub)) return@launch
                    val hex = Bytes.hex(packet.senderPk)
                    val sm = SessionManager(id)
                    val st = sm.sessionFor(packet.senderPk) ?: sm.ensureSessionFor(packet.senderPk)
                    if (!st.established) return@launch
                    val innerBytes = try { st.decrypt(envelope.ciphertext) } catch (e: Exception) { return@launch }
                    val msg = try { InnerMessage.decode(innerBytes) } catch (e: Exception) { return@launch }
                    persistIncoming(packet.senderPk, msg, packet)
                }
                MeshEnvelope.KIND_ROOM -> {
                    val innerBytes = try {
                        com.squelch.app.crypto.AesGcm.decrypt(roomKey(envelope.recipient), envelope.ciphertext)
                    } catch (e: Exception) { return@launch }
                    val msg = try { InnerMessage.decode(innerBytes) } catch (e: Exception) { return@launch }
                    if (Db.isOpen()) {
                        Db.requireDb().messages().insert(
                            com.squelch.app.db.MessageEntity(
                                conversationId = Bytes.hex(envelope.recipient),
                                msgId = Bytes.hex(msg.msgId),
                                sender = Bytes.hex(packet.senderPk),
                                body = msg.text,
                                timestamp = msg.timestamp,
                                direction = 0,
                                delivery = 3,
                                kind = 1
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun persistIncoming(senderPk: ByteArray, msg: InnerMessage, packet: MeshPacket) {
        _messages.value = _messages.value + SignedMessage(
            fromPub = Bytes.hex(packet.senderPk),
            inner = msg,
            timestamp = packet.msgId.joinToString("") { "%02x".format(it) }
        )
        if (Db.isOpen()) {
            Db.requireDb().messages().insert(
                com.squelch.app.db.MessageEntity(
                    conversationId = Bytes.hex(senderPk),
                    msgId = Bytes.hex(msg.msgId),
                    sender = Bytes.hex(packet.senderPk),
                    body = msg.text,
                    timestamp = msg.timestamp,
                    direction = 0,
                    delivery = 3,
                    kind = 0
                )
            )
        }
    }

    private suspend fun persistOutgoing(peerEd: ByteArray, msg: InnerMessage, packet: MeshPacket) {
        _messages.value = _messages.value + SignedMessage(
            fromPub = Bytes.hex(peerEd),
            inner = msg,
            timestamp = Bytes.hex(packet.msgId)
        )
        if (Db.isOpen()) {
            Db.requireDb().messages().insert(
                com.squelch.app.db.MessageEntity(
                    conversationId = Bytes.hex(peerEd),
                    msgId = Bytes.hex(msg.msgId),
                    sender = "me",
                    body = msg.text,
                    timestamp = msg.timestamp,
                    direction = 1,
                    delivery = 1,
                    kind = 0
                )
            )
        }
    }

    private fun publish() {
        _status.value = _status.value.copy(linkedPeers = _peers.value.size)
    }

    data class MeshStatus(
        val running: Boolean = false,
        val linkedPeers: Int = 0,
        val startedAt: Long = 0L,
        val lastError: String? = null
    )

    data class MeshPeer(
        val endpointId: String,
        val displayName: String,
        val since: Long
    )

    data class SignedMessage(
        val fromPub: String,
        val inner: InnerMessage,
        val timestamp: String
    )

    companion object {
        const val KIND_HELLO: Byte = 0x01
        const val KIND_HANDSHAKE: Byte = 0x03
        const val KIND_DATA: Byte = 0x02
    }
}

/** Named trust levels for `ContactEntity.trustLevel` (kept adjacent to MeshEngine for code locality). */
object TrustLevel {
    const val MET = 0
    const val VERIFIED = 1
    const val RELAYED = 2
}
