package com.squelch.app.mesh

import android.content.Context
import com.squelch.app.crypto.IdentityManager
import com.squelch.app.db.AppDatabase
import com.squelch.app.db.ContactEntity
import com.squelch.app.transport.MeshLink
import com.squelch.app.transport.MeshLinkListener
import com.squelch.app.transport.ble.BleTransport
import com.squelch.app.transport.wifi.WifiDirectTransport
import com.squelch.app.util.Bytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The mesh coordinator (spec 2.1 message/routing layers): owns transports,
 * routing (TTL flood + dedup + store-and-forward), E2E sessions, conversations
 * and rooms. Every byte from every radio funnels through here.
 */
class MeshEngine(
    private val context: Context,
    val identityManager: IdentityManager,
    private val db: AppDatabase
) : MeshLinkListener {

    val identity get() = identityManager.identity
    val callsign get() = identityManager.callsign

    val peers = PeerRegistry()
    val sessionManager = SessionManager(this)
    val messageLayer = MessageLayer(this, db)
    val rooms = RoomManager(db)

    private val storeForward = StoreAndForward()
    private val seen = LinkedHashMap<String, Long>()
    private val linkPeers = ConcurrentHashMap<MeshLink, String>() // link -> peer edHex
    private val pendingNfcNonces = ConcurrentHashMap<String, ByteArray>() // peer edHex -> nonce
    private val sentHelloOn = HashSet<MeshLink>()

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var ble: BleTransport? = null
    private var wifi: WifiDirectTransport? = null
    private var rekeyJob: Job? = null

    @Volatile
    var started = false
        private set

    @Volatile
    var status = MeshStatus()
        private set

    data class MeshStatus(
        var links: Int = 0,
        var peers: Int = 0,
        var storeForwarded: Int = 0,
        var bleActive: Boolean = false,
        var wifiActive: Boolean = false,
        var packetsSeen: Long = 0,
        var packetsForwarded: Long = 0
    )

    // ---------------- lifecycle ----------------

    fun start() {
        if (started) return
        started = true
        val caps = Hello.CAP_BLE or Hello.CAP_WIFI or Hello.CAP_NFC

        ble = BleTransport(context, this, identity.edPub.copyOf(8)).also { it.start() }
        wifi = WifiDirectTransport(context, this).also { it.start() }

        rekeyJob = scope.launch {
            while (true) {
                delay(10 * 60 * 1000)
                sessionManager.rekeyAll()
            }
        }
        updateStatus()
    }

    fun stop() {
        if (!started) return
        started = false
        rekeyJob?.cancel()
        ble?.stop()
        wifi?.stop()
        ble = null
        wifi = null
        linkPeers.clear()
        updateStatus()
    }

    fun onPermissionsChanged() {
        // Re-start BLE transport in case permissions were granted.
        if (started) {
            ble?.stop()
            ble = BleTransport(context, this, identity.edPub.copyOf(8)).also { it.start() }
        }
    }

    private fun updateStatus() {
        status = MeshStatus(
            links = linkPeers.size,
            peers = peers.size(),
            storeForwarded = storeForward.size(),
            bleActive = started,
            wifiActive = false,
            packetsSeen = status.packetsSeen,
            packetsForwarded = status.packetsForwarded
        )
    }

    // ---------------- transport callbacks ----------------

    override fun onLinkOpen(link: MeshLink) {
        scope.launch {
            // Send our HELLO; the peer responds with theirs.
            if (!sentHelloOn.contains(link)) {
                sentHelloOn.add(link)
                link.sendFrame(LinkCodec.KIND_HELLO, myHello())
            }
            updateStatus()
        }
    }

    override fun onFrame(link: MeshLink, kind: Byte, message: ByteArray) {
        scope.launch {
            when (kind) {
                LinkCodec.KIND_HELLO -> onHello(link, Hello.decode(message))
                LinkCodec.KIND_MESH -> {
                    val packet = try {
                        MeshPacket.decode(message)
                    } catch (e: Exception) {
                        return@launch
                    }
                    onMeshPacket(packet, link)
                }
                WifiDirectTransport.KIND_WIFI_OFFER -> {
                    WifiDirectTransport.decodeOffer(message)?.let { offer ->
                        wifi?.onOffer(offer, "")
                    }
                }
            }
        }
    }

    override fun onLinkClosed(link: MeshLink) {
        scope.launch {
            val peerHex = linkPeers.remove(link)
            sentHelloOn.remove(link)
            if (peerHex != null) {
                peers.getByHex(peerHex)?.let { p ->
                    if (p.link === link) p.setLink(null)
                }
            }
            updateStatus()
        }
    }

    // ---------------- HELLO ----------------

    private fun onHello(link: MeshLink, hello: Hello) {
        if (hello.edPub.isEmpty() || hello.edPub.size != 32) return
        val peer = peers.get(hello.edPub)
            ?: Peer(hello.edPub, hello.xPub, hello.callsign, TrustLevel.MET, hello.capabilities, hello.address).also {
                peers.register(it)
            }
        if (peer.xPub.isEmpty()) peer.xPub = hello.xPub
        if (peer.callsign.isEmpty()) peer.callsign = hello.callsign
        peer.capabilities = hello.capabilities
        if (hello.address.isNotEmpty()) peer.bluetoothAddress = hello.address
        peer.setLink(link)
        linkPeers[link] = peer.edHex
        persistPeer(peer)

        if (!sentHelloOn.contains(link)) {
            sentHelloOn.add(link)
            link.sendFrame(LinkCodec.KIND_HELLO, myHello())
        }

        // Flush anything waiting for this peer.
        storeForward.takeFor(peer.edHex).forEach { p ->
            link.sendFrame(LinkCodec.KIND_MESH, p.encode())
        }

        // NFC tap happened earlier? Start the session so the nonce returns now.
        pendingNfcNonces.remove(peer.edHex)?.let { nonce ->
            if (sessionManager.getSession(peer.edHex) == null) {
                sessionManager.createInitiatorSession(peer, nonce)
            }
        }
        updateStatus()
    }

    fun onPeerHandshakeHello(peerEd: ByteArray, helloBytes: ByteArray) {
        val hello = try {
            Hello.decode(helloBytes)
        } catch (e: Exception) {
            return
        }
        if (hello.edPub.isEmpty()) return
        val peer = peers.get(hello.edPub)
            ?: Peer(hello.edPub, hello.xPub, hello.callsign, TrustLevel.MET, hello.capabilities, hello.address).also {
                peers.register(it)
            }
        if (peer.xPub.isEmpty()) peer.xPub = hello.xPub
        if (peer.callsign.isEmpty()) peer.callsign = hello.callsign
        peer.capabilities = hello.capabilities
        peer.lastSeen = System.currentTimeMillis()
        persistPeer(peer)
        updateStatus()
    }

    fun myHello(): ByteArray = Hello(
        edPub = identity.edPub,
        xPub = identity.xPub,
        callsign = callsign,
        capabilities = Hello.CAP_BLE or Hello.CAP_WIFI or Hello.CAP_NFC,
        deviceName = android.os.Build.MODEL
    ).encode()

    private fun persistPeer(peer: Peer) {
        scope.launch {
            db.contacts().upsert(
                ContactEntity(
                    pubkey = peer.edHex,
                    xPub = peer.xHex,
                    callsign = peer.callsign,
                    trustLevel = peer.trustLevel,
                    capabilities = peer.capabilities,
                    lastSeen = peer.lastSeen,
                    bluetoothAddress = peer.bluetoothAddress,
                    mutualStatics = peer.mutualStatics
                )
            )
        }
    }

    // ---------------- routing ----------------

    fun onMeshPacket(packet: MeshPacket, fromLink: MeshLink?) {
        val idHex = Bytes.hex(packet.msgId)
        synchronized(seen) {
            if (seen.containsKey(idHex)) return
            seen[idHex] = System.currentTimeMillis()
            while (seen.size > 1024) seen.remove(seen.keys.first())
        }
        status = status.copy(packetsSeen = status.packetsSeen + 1)

        if (!packet.verifySignature()) return

        val envelope = try {
            MeshEnvelope.decode(packet.payload)
        } catch (e: Exception) {
            return
        }

        when (envelope.kind) {
            MeshEnvelope.KIND_HS -> {
                if (envelope.addressedTo(identity.edPub)) {
                    sessionManager.onHandshake(packet, envelope)
                } else {
                    forwardOrStore(packet, envelope, fromLink)
                }
            }
            MeshEnvelope.KIND_DM -> {
                if (envelope.addressedTo(identity.edPub)) {
                    decryptDm(packet, envelope)
                } else {
                    forwardOrStore(packet, envelope, fromLink)
                }
            }
            MeshEnvelope.KIND_ROOM -> {
                decryptRoom(packet, envelope)
                forwardOrStore(packet, envelope, fromLink)
            }
        }
    }

    private fun forwardOrStore(packet: MeshPacket, envelope: MeshEnvelope, fromLink: MeshLink?) {
        if (packet.ttl <= 1) return
        val next = packet.withTtl(packet.ttl - 1)
        status = status.copy(packetsForwarded = status.packetsForwarded + 1)
        flood(next, fromLink)

        // Store-and-forward for the addressed recipient if not directly linked.
        val recipientHex = Bytes.hex(envelope.recipient)
        val recipient = peers.getByHex(recipientHex)
        if (recipient == null || recipient.link == null) {
            if (!envelope.addressedTo(identity.edPub)) {
                storeForward.store(recipientHex, next)
            }
        }
        updateStatus()
    }

    /** Deliver a packet toward a recipient: direct link if we have one, else flood. */
    fun sendToPeer(recipientEd: ByteArray, packet: MeshPacket): Boolean {
        val peer = peers.get(recipientEd)
        val link = peer?.link
        return if (link != null) {
            link.sendFrame(LinkCodec.KIND_MESH, packet.encode())
            true
        } else {
            flood(packet, null)
            storeForward.store(Bytes.hex(recipientEd), packet)
            updateStatus()
            false
        }
    }

    private fun flood(packet: MeshPacket, exceptLink: MeshLink?) {
        val bytes = packet.encode()
        for ((link, _) in linkPeers) {
            if (link !== exceptLink) {
                try {
                    link.sendFrame(LinkCodec.KIND_MESH, bytes)
                } catch (e: Exception) {
                }
            }
        }
    }

    // ---------------- send paths ----------------

    fun sendHandshake(peerEd: ByteArray, hsBytes: ByteArray) {
        val envelope = MeshEnvelope(MeshEnvelope.KIND_HS, peerEd, hsBytes)
        val packet = signPacket(peerEd, envelope)
        sendToPeer(peerEd, packet)
    }

    fun sendEncryptedDm(peer: Peer, inner: InnerMessage) {
        val ct = sessionManager.encryptDm(peer, inner.encode()) ?: return
        val envelope = MeshEnvelope(MeshEnvelope.KIND_DM, peer.edPub, ct)
        val packet = signPacket(peer.edPub, envelope, inner.msgId)
        val direct = sendToPeer(peer.edPub, packet)
        if (direct) messageLayer.markSent(inner.msgId) else messageLayer.markQueued(inner.msgId)
    }

    fun sendRoom(room: RoomManager.Room, encodedInner: ByteArray) {
        val ct = rooms.encrypt(room, encodedInner)
        val envelope = MeshEnvelope(MeshEnvelope.KIND_ROOM, room.id, ct)
        val msgId = Bytes.randomId(java.security.SecureRandom())
        val packet = signPacket(room.id, envelope, msgId)
        flood(packet, null)
    }

    fun sendRoomJoin(room: RoomManager.Room) {
        val now = System.currentTimeMillis()
        sendRoom(room, InnerMessage.joinRoom(now, Bytes.randomId(java.security.SecureRandom()), room.name).encode())
    }

    fun sendRoomLeave(room: RoomManager.Room) {
        val now = System.currentTimeMillis()
        sendRoom(room, InnerMessage.leaveRoom(now, Bytes.randomId(java.security.SecureRandom()), room.name).encode())
    }

    private fun signPacket(recipient: ByteArray, envelope: MeshEnvelope, msgId: ByteArray = Bytes.randomId(java.security.SecureRandom())): MeshPacket {
        val payload = envelope.encode()
        val signed = Bytes.concat(msgId, payload)
        val sig = com.squelch.app.crypto.Ed25519.sign(identity.edSeed, signed)
        return MeshPacket(msgId, MeshPacket.DEFAULT_TTL, identity.edPub, sig, payload)
    }

    // ---------------- inbound decryption ----------------

    private fun decryptDm(packet: MeshPacket, envelope: MeshEnvelope) {
        val sender = peers.get(packet.senderPk)
            ?: Peer(packet.senderPk.copyOf(), ByteArray(32), "UNKNOWN", TrustLevel.MET).also { peers.register(it) }
        val plaintext = sessionManager.decryptDm(packet.senderPk, envelope.ciphertext) ?: return
        val inner = try {
            InnerMessage.decode(plaintext)
        } catch (e: Exception) {
            return
        }
        messageLayer.onDmReceived(sender, inner)
    }

    private fun decryptRoom(packet: MeshPacket, envelope: MeshEnvelope) {
        val room = rooms.findById(envelope.recipient) ?: return
        val plaintext = rooms.decrypt(room, envelope.ciphertext) ?: return
        val inner = try {
            InnerMessage.decode(plaintext)
        } catch (e: Exception) {
            return
        }
        messageLayer.onRoomReceived(room, packet.senderPk, inner)
    }

    // ---------------- NFC ----------------

    /** Reader side: another device's identity was read via NFC tap. */
    fun onNfcIdentityRead(edPub: ByteArray, xPub: ByteArray, nonce: ByteArray) {
        scope.launch {
            val peer = peers.get(edPub)
                ?: Peer(edPub, xPub, "TAPPED", TrustLevel.VERIFIED).also { peers.register(it) }
            peer.trustLevel = TrustLevel.VERIFIED
            if (peer.xPub.isEmpty()) peer.xPub = xPub
            persistPeer(peer)

            // Remember the nonce; once a link opens to this peer we start the session.
            pendingNfcNonces[peer.edHex] = nonce

            // If we're already linked (unlikely this fast), start immediately.
            if (peer.link != null && sessionManager.getSession(peer.edHex) == null) {
                sessionManager.createInitiatorSession(peer, nonce)
            }
        }
    }

    /** Host side: our HCE tag was read and the nonce came back over the session. */
    fun onNfcNonceReturned(peerEd: ByteArray, nonce: ByteArray) {
        val served = com.squelch.app.transport.nfc.HceNdefService.lastNonce ?: return
        if (Bytes.constantTimeEquals(served, nonce)) {
            scope.launch {
                val peer = peers.get(peerEd) ?: return@launch
                peer.trustLevel = TrustLevel.VERIFIED
                persistPeer(peer)
                updateStatus()
            }
        }
    }

    fun onSessionEstablished(peer: Peer) {
        persistPeer(peer)
        maybeEscalateToWifi(peer)
    }

    fun onSessionError(peerEd: ByteArray) {
        peers.get(peerEd)?.let { it.setLink(null) }
    }

    // ---------------- WiFi escalation (M5) ----------------

    @Volatile
    private var recentVolume = HashMap<String, Long>() // peer edHex -> bytes

    private fun maybeEscalateToWifi(peer: Peer) {
        val wifiT = wifi ?: return
        if (!wifiT.supported) return
        if (!peer.supports(Hello.CAP_WIFI)) return
        val link = peer.link ?: return
        if (link.transportName == "WiFi") return
        // Escalate when both peers are WiFi-capable and we have an active session.
        wifiT.createGroupAsOwner(peer.edHex)
        // The owner's offer is sent over the existing link so the peer can join.
        wifiT.offerCurrentGroupIfOwner()?.let { offer ->
            link.sendFrame(WifiDirectTransport.KIND_WIFI_OFFER, WifiDirectTransport.encodeOffer(offer))
        }
    }

    // ---------------- settings helpers ----------------

    fun applyHistoryRetention(retentionMs: Long) {
        scope.launch {
            db.messages().purgeBefore(System.currentTimeMillis() - retentionMs)
        }
    }
}
