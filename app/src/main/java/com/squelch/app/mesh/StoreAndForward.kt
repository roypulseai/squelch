package com.squelch.app.mesh

import com.squelch.app.util.Bytes
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-peer queue for messages that couldn't be delivered immediately.
 * Spec section 5.2 store-and-forward. Capacity is bounded so a chatty
 * peer can't fill RAM; older messages fall off when the cap is hit.
 *
 * Wired into [MeshEngine]:
 *   - sendChat() -> on link present, broadcast. Otherwise enqueue.
 *   - onEndpointConnected() -> flush every queued packet for that peer.
 */
class StoreAndForward(
    private val maxPerPeer: Int = 200,
    private val maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000  // 30 days
) {

    data class Queued(
        val encoded: ByteArray,
        val kind: Byte,
        val peerEd: ByteArray,
        val enqueuedAt: Long
    )

    /** peer edPub hex -> queue */
    private val queues = ConcurrentHashMap<String, ArrayDeque<Queued>>()

    /** Used by the engine to track the most-recently-seen timestamp of
     *  each peer so a delivery status can read "online N minutes ago". */
    private val lastSeen = ConcurrentHashMap<String, Long>()

    fun enqueue(peerEd: ByteArray, kind: Byte, encoded: ByteArray) {
        val key = Bytes.hex(peerEd)
        val q = queues.computeIfAbsent(key) { ArrayDeque() }
        q.addLast(Queued(encoded = encoded.copyOf(), kind = kind,
            peerEd = peerEd.copyOf(), enqueuedAt = System.currentTimeMillis()))
        while (q.size > maxPerPeer) q.removeFirst()
    }

    /** Drop everything older than maxAgeMs and return what's left. */
    fun take(peerEd: ByteArray): List<Queued> {
        val key = Bytes.hex(peerEd)
        val q = queues.remove(key) ?: return emptyList()
        val now = System.currentTimeMillis()
        return q.filter { now - it.enqueuedAt <= maxAgeMs }
    }

    fun sizeFor(peerEd: ByteArray): Int = queues[Bytes.hex(peerEd)]?.size ?: 0
    fun totalSize(): Int = queues.values.sumOf { it.size }
    fun hasPendingFor(peerEd: ByteArray): Boolean = (queues[Bytes.hex(peerEd)]?.size ?: 0) > 0

    fun noteSeen(peerEd: ByteArray) {
        lastSeen[Bytes.hex(peerEd)] = System.currentTimeMillis()
    }

    fun lastSeenMs(peerEd: ByteArray): Long = lastSeen[Bytes.hex(peerEd)] ?: 0L
}
