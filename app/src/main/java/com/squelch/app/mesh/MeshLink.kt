package com.squelch.app.mesh

/**
 * One connected link to a single remote peer over any transport.
 *
 * Each implementation (Nearby via [AndroidMeshManager], WebSocket relay,
 * future libp2p) emits the same kind-byte frame format (HELLO=0x01,
 * DATA=0x02, HS=0x03) so the [MeshEngine] doesn't care which transport
 * carries a frame.
 */
interface MeshLink {
    val transportName: String
    val remoteId: String
    fun sendFrame(kind: Byte, body: ByteArray)
    fun close()
}