package com.squelch.app.mesh

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Store-and-forward cache (spec 5.2): packets addressed to peers that are not
 * currently reachable are cached (encrypted — forwarding nodes can't read them)
 * and flushed when the peer's identity is next seen on a link.
 */
class StoreAndForward(private val maxPerPeer: Int = 200, private val maxAgeMs: Long = 30 * 60 * 1000L) {

    private val queues = ConcurrentHashMap<String, ConcurrentLinkedQueue<Stored>>()

    data class Stored(val packet: MeshPacket, val addedAt: Long)

    fun store(recipientEdHex: String, packet: MeshPacket) {
        val q = queues.getOrPut(recipientEdHex) { ConcurrentLinkedQueue() }
        q.offer(Stored(packet, System.currentTimeMillis()))
        while (q.size > maxPerPeer) q.poll()
    }

    fun takeFor(recipientEdHex: String): List<MeshPacket> {
        val q = queues.remove(recipientEdHex) ?: return emptyList()
        val now = System.currentTimeMillis()
        return q.filter { now - it.addedAt <= maxAgeMs }.map { it.packet }
    }

    fun size(): Int = queues.values.sumOf { it.size }

    fun hasFor(recipientEdHex: String): Boolean = queues[recipientEdHex]?.isNotEmpty() ?: false
}
