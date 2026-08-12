package com.squelch.app.mesh

import com.squelch.app.transport.MeshLink
import com.squelch.app.util.Bytes
import java.util.concurrent.ConcurrentHashMap

object TrustLevel {
    const val MET = 0
    const val VERIFIED = 1
    const val RELAYED = 2

    fun glyph(level: Int): String = when (level) {
        VERIFIED -> "V"
        RELAYED -> "R"
        else -> "M"
    }
}

/** A known peer, keyed by its Ed25519 public key (spec 3 uniqueness note). */
class Peer(
    val edPub: ByteArray,
    var xPub: ByteArray,
    var callsign: String,
    var trustLevel: Int,
    var capabilities: Int = 0,
    var bluetoothAddress: String = "",
    var mutualStatics: Boolean = false
) {
    val edHex: String get() = Bytes.hex(edPub)
    val xHex: String get() = Bytes.hex(xPub)

    @Volatile
    var link: MeshLink? = null
        private set

    @Volatile
    var lastSeen: Long = System.currentTimeMillis()

    fun setLink(l: MeshLink?) {
        link = l
        if (l != null) lastSeen = System.currentTimeMillis()
    }

    fun supports(cap: Int): Boolean = capabilities and cap != 0
}

/**
 * In-memory peer registry. Call-sign collisions are never merged: entries stay
 * keyed by full public key and the UI disambiguates identical call-signs (spec 3).
 */
class PeerRegistry {
    private val peers = ConcurrentHashMap<String, Peer>()

    val all: Collection<Peer> get() = peers.values

    fun get(edPub: ByteArray): Peer? = peers[Bytes.hex(edPub)]

    fun getByHex(edHex: String): Peer? = peers[edHex]

    fun getByXPub(xPub: ByteArray): Peer? {
        val target = Bytes.hex(xPub)
        return peers.values.firstOrNull { it.xHex == target }
    }

    fun register(peer: Peer): Peer {
        val prev = peers.put(peer.edHex, peer)
        return peer
    }

    fun remove(edHex: String) {
        peers.remove(edHex)
    }

    fun clear() = peers.clear()

    /** Display call-sign; on collision (same call-sign, different key) append a suffix. */
    fun displayCallsign(edHex: String): String {
        val p = peers[edHex] ?: return "????-????-????"
        val colliding = peers.values.filter { it.callsign == p.callsign && it.edHex != edHex }
            .sortedBy { it.edHex }
        if (colliding.isEmpty()) return p.callsign
        val index = colliding.indexOfFirst { it.edHex > edHex } + 1
        return com.squelch.app.crypto.Callsign.disambiguate(p.callsign, index + 1)
    }

    fun size(): Int = peers.size
}
