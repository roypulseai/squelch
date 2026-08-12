package com.squelch.app.transport

/** A connected, identified (or identifying) link to a nearby peer over one transport. */
interface MeshLink {
    val transportName: String
    val isNoiseInitiator: Boolean
    fun sendFrame(kind: Byte, message: ByteArray)
    fun close()
}

/** Callbacks from the transport layer into the mesh engine. */
interface MeshLinkListener {
    fun onLinkOpen(link: MeshLink)
    fun onFrame(link: MeshLink, kind: Byte, message: ByteArray)
    fun onLinkClosed(link: MeshLink)
}

/** A transport that can discover peers and open links to them. */
interface MeshTransport {
    val name: String
    val supported: Boolean
    fun start()
    fun stop()
}
