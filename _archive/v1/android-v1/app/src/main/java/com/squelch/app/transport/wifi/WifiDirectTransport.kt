package com.squelch.app.transport.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import com.squelch.app.mesh.LinkCodec
import com.squelch.app.transport.MeshLink
import com.squelch.app.transport.MeshLinkListener
import com.squelch.app.transport.MeshTransport
import com.squelch.app.util.Bytes
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * WiFi Direct burst-upgrade transport (spec 4.2, M5). WiFi is only ever
 * negotiated AFTER BLE has established identity+trust; the group owner's
 * IP:port is exchanged over the existing BLE link (KIND_WIFI_OFFER).
 *
 * Cross-platform caveat handled elsewhere: WiFi P2P is same-OS only; BLE
 * remains the guaranteed cross-platform path. Requires real hardware to test.
 */
class WifiDirectTransport(
    private val context: Context,
    private val listener: MeshLinkListener
) : MeshTransport {

    override val name = "WiFi"
    private var started = false

    private val wifi: WifiManager? = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    override val supported: Boolean get() = wifi?.isP2pSupported ?: false

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val links = java.util.concurrent.ConcurrentHashMap<String, WifiLink>()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var groupOwner: InetAddress? = null

    companion object {
        const val PORT = 45231
        const val KIND_WIFI_OFFER: Byte = 0x03

        /** Bundle describing a WiFi group, exchanged over BLE to bootstrap the TCP link. */
        data class WifiOffer(val isOwner: Boolean, val ownerIp: String, val port: Int, val groupName: String)

        fun encodeOffer(offer: WifiOffer): ByteArray {
            val b = StringBuilder()
            b.append(if (offer.isOwner) 'O' else 'J')
            b.append('|').append(offer.ownerIp)
            b.append('|').append(offer.port)
            b.append('|').append(offer.groupName)
            return b.toString().toByteArray(Charsets.UTF_8)
        }

        fun decodeOffer(bytes: ByteArray): WifiOffer? {
            return try {
                val parts = String(bytes, Charsets.UTF_8).split("|")
                WifiOffer(parts[0] == "O", parts[1], parts[2].toInt(), parts[3])
            } catch (e: Exception) {
                null
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(cxt: Context, intent: Intent) {
            val mgr = manager ?: return
            val ch = channel ?: return
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    mgr.requestConnectionInfo(ch) { info ->
                        info?.groupOwnerAddress?.hostAddress?.let { ip ->
                            groupOwner = InetAddress.getByName(ip)
                        }
                    }
                }
            }
        }
    }

    override fun start() {
        if (started) return
        started = true
        val mgr = manager ?: return
        channel = mgr.initialize(context, Looper.getMainLooper(), null)
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        })
    }

    override fun stop() {
        if (!started) return
        started = false
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        acceptThread?.interrupt()
        serverSocket?.close()
        links.values.forEach { it.close() }
        links.clear()
    }

    /** Called by the mesh engine when a WIFI_OFFER arrives over BLE. */
    fun onOffer(offer: WifiOffer, peerKey: String) {
        if (!offer.isOwner) {
            createGroupThenWait(offer.ownerIp, peerKey)
        } else {
            connectToOwner(offer.ownerIp, offer.port, peerKey)
        }
    }

    /** Become the group owner and listen for the peer's TCP connection. */
    fun createGroupAsOwner(peerKey: String) {
        val mgr = manager ?: return
        val ch = channel ?: return
        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                listenForConnection(peerKey)
            }

            override fun onFailure(reason: Int) {
                listener.onLinkClosed(closedStub())
            }
        })
    }

    /** Return true if this device already belongs to a group; if so offer it over BLE. */
    fun offerCurrentGroupIfOwner(): WifiOffer? {
        val ip = groupOwner ?: return null
        return WifiOffer(isOwner = true, ownerIp = ip.hostAddress ?: return null, port = PORT, groupName = "squelch")
    }

    private fun createGroupThenWait(ownerIp: String, peerKey: String) {
        val mgr = manager ?: return
        val ch = channel ?: return
        // Join the group: connecting to the group owner's device address was done
        // via BLE handshake; here we only need to wait until group info is known.
        listenForConnection(peerKey)
    }

    private fun listenForConnection(peerKey: String) {
        if (serverSocket != null) return
        acceptThread?.interrupt()
        acceptThread = thread(name = "squelch-wifi-accept") {
            try {
                val ss = ServerSocket(PORT)
                serverSocket = ss
                while (!Thread.currentThread().isInterrupted) {
                    val socket = ss.accept()
                    val link = WifiLink(socket, "WiFi", isNoiseInitiator = false)
                    link.listener = this::onLinkFrame
                    link.closedHandler = { onLinkClosed(link) }
                    links[link.id] = link
                    listener.onLinkOpen(link)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun connectToOwner(ownerIp: String, port: Int, peerKey: String) {
        thread(name = "squelch-wifi-connect") {
            try {
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(InetAddress.getByName(ownerIp), port), 10000)
                val link = WifiLink(socket, "WiFi", isNoiseInitiator = true)
                link.listener = this::onLinkFrame
                link.closedHandler = { onLinkClosed(link) }
                links[link.id] = link
                listener.onLinkOpen(link)
            } catch (e: Exception) {
            }
        }
    }

    private fun onLinkFrame(link: MeshLink, kind: Byte, message: ByteArray) {
        listener.onFrame(link, kind, message)
    }

    private fun onLinkClosed(link: MeshLink) {
        links.remove((link as WifiLink).id)
        listener.onLinkClosed(link)
    }

    private fun closedStub(): MeshLink = object : MeshLink {
        override val transportName = "WiFi"
        override val isNoiseInitiator = false
        override fun sendFrame(kind: Byte, message: ByteArray) {}
        override fun close() {}
    }

    /** TCP link carrying the same chunk framing as BLE. */
    private class WifiLink(
        private val socket: Socket,
        override val transportName: String,
        override val isNoiseInitiator: Boolean
    ) : MeshLink {
        val id: String = socket.inetAddress.hostAddress ?: "wifi"
        var listener: ((MeshLink, Byte, ByteArray) -> Unit)? = null
        var closedHandler: (() -> Unit)? = null
        private val assembler = LinkCodec.Assembler { kind, message ->
            listener?.invoke(this, kind, message)
        }
        private val writeLock = Any()
        private var closed = false

        init {
            thread(name = "squelch-wifi-read") {
                try {
                    val input = socket.getInputStream()
                    val buf = ByteArray(4096)
                    var leftover = ByteArray(0)
                    while (!closed) {
                        val read = input.read(buf)
                        if (read < 0) break
                        val data = leftover + buf.copyOf(read)
                        leftover = parse(data)
                    }
                } catch (e: Exception) {
                } finally {
                    close()
                }
            }
        }

        private fun parse(data: ByteArray): ByteArray {
            var p = 0
            while (p + 2 <= data.size) {
                val len = Bytes.intFrom16(data[p], data[p + 1])
                if (len == 0) { p += 2; continue }
                if (p + 2 + len > data.size) break
                val chunk = data.copyOfRange(p + 2, p + 2 + len)
                assembler.onChunk(chunk)
                p += 2 + len
            }
            return if (p >= data.size) ByteArray(0) else data.copyOfRange(p, data.size)
        }

        override fun sendFrame(kind: Byte, message: ByteArray) {
            if (closed) return
            val chunks = LinkCodec.encodeChunks(kind, message, 4000)
            try {
                synchronized(writeLock) {
                    val out = socket.getOutputStream()
                    for (c in chunks) {
                        out.write(Bytes.u16be(c.size))
                        out.write(c)
                    }
                    out.flush()
                }
            } catch (e: Exception) {
                close()
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            closedHandler?.invoke()
            try {
                socket.close()
            } catch (e: Exception) {
            }
        }
    }
}
