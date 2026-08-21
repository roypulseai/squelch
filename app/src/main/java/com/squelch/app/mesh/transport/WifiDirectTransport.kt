package com.squelch.app.mesh.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import com.squelch.app.util.Bytes
import com.squelch.app.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class WifiDirectTransport(
    private val context: Context,
    private val selfEdPubHex: String
) : Transport {

    companion object {
        private const val TAG = "WifiDirectTransport"
        private const val SOCKET_PORT = 8888
    }

    override val name: String = "WiFi-Direct"

    private val _incoming = MutableSharedFlow<Transport.TransportFrame>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<Transport.TransportFrame> = _incoming.asSharedFlow()

    private var scope: CoroutineScope? = null
    @Volatile private var running = false

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _peers = MutableStateFlow<List<WifiDirectPeer>>(emptyList())
    val peers: StateFlow<List<WifiDirectPeer>> = _peers.asStateFlow()

    private val peerSockets = ConcurrentHashMap<String, Socket>()
    private val peerPubkeys = ConcurrentHashMap<String, String>()
    private val pubkeyToAddress = ConcurrentHashMap<String, String>()
    private var serverSocket: ServerSocket? = null
    @Volatile private var isGroupOwner = false
    private var groupOwnerAddress: String? = null

    data class WifiDirectPeer(
        val deviceName: String,
        val deviceAddress: String,
        val status: String,
        val isAvailable: Boolean
    )

    override fun start() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        running = true
        initWifiP2p()
        startServerSocket()
        Log.d(TAG, "WiFi Direct transport started")
    }

    override fun stop() {
        running = false
        try { receiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
        receiver = null
        for ((_, socket) in peerSockets) {
            try { socket.close() } catch (_: Exception) {}
        }
        peerSockets.clear()
        peerPubkeys.clear()
        pubkeyToAddress.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        try { manager?.removeGroup(channel, null) } catch (_: Exception) {}
        scope?.cancel()
        scope = null
        Log.d(TAG, "WiFi Direct transport stopped")
    }

    override fun send(recipientEdPubHex: String, payload: ByteArray) {
        if (!running) return
        scope?.launch(Dispatchers.IO) {
            val address = pubkeyToAddress[recipientEdPubHex]
            if (address != null) {
                sendToConnectedPeer(recipientEdPubHex, address, payload)
            } else {
                connectAndSend(recipientEdPubHex, payload)
            }
        }
    }

    private fun initWifiP2p() {
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, Looper.getMainLooper(), null)

        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED
                        )
                        if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            Log.w(TAG, "Wi-Fi P2P disabled")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        requestPeers()
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        requestConnectionInfo()
                    }
                }
            }
        }

        try {
            context.registerReceiver(receiver, intentFilter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver: ${e.message}")
        }

        discoverPeers()
    }

    fun discoverPeers() {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { Log.d(TAG, "Discovery started") }
                override fun onFailure(reason: Int) { Log.e(TAG, "Discovery failed: $reason") }
            })
        } catch (e: Exception) {
            Log.e(TAG, "discoverPeers crashed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val mgr = manager ?: return
        val ch = channel ?: return
        mgr.requestPeers(ch) { peerList: WifiP2pDeviceList ->
            val peerDevices = peerList.deviceList.map { device ->
                WifiDirectPeer(
                    deviceName = device.deviceName.ifEmpty { "Unknown" },
                    deviceAddress = device.deviceAddress,
                    status = when (device.status) {
                        WifiP2pDevice.AVAILABLE -> "Available"
                        WifiP2pDevice.INVITED -> "Invited"
                        WifiP2pDevice.CONNECTED -> "Connected"
                        WifiP2pDevice.FAILED -> "Failed"
                        WifiP2pDevice.UNAVAILABLE -> "Unavailable"
                        else -> "Unknown"
                    },
                    isAvailable = device.status == WifiP2pDevice.AVAILABLE
                )
            }
            _peers.value = peerDevices
            Log.d(TAG, "Found ${peerDevices.size} peers")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        val mgr = manager ?: return
        val ch = channel ?: return
        mgr.requestConnectionInfo(ch) { info: WifiP2pInfo ->
            if (info.groupFormed && info.isGroupOwner) {
                isGroupOwner = true
                groupOwnerAddress = null
                Log.d(TAG, "We are group owner, server socket already running")
            } else if (info.groupFormed) {
                isGroupOwner = false
                val goAddr = info.groupOwnerAddress.hostAddress
                if (goAddr != null) {
                    groupOwnerAddress = goAddr
                    Log.d(TAG, "We are client, GO=$goAddr")
                    scope?.launch(Dispatchers.IO) {
                        connectToGroupOwner(goAddr)
                    }
                }
            }
        }
    }

    private fun startServerSocket() {
        scope?.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(SOCKET_PORT)
                Log.d(TAG, "Server socket started on port $SOCKET_PORT")
                while (running && serverSocket != null) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        Log.d(TAG, "Incoming socket connection from ${client.inetAddress}")
                        handleSocketConnection(client)
                    } catch (e: Exception) {
                        if (running) Log.e(TAG, "Server socket accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket failed: ${e.message}")
            }
        }
    }

    private fun connectToGroupOwner(goAddress: String) {
        try {
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(goAddress, SOCKET_PORT), 5000)
            Log.d(TAG, "Connected to GO at $goAddress:$SOCKET_PORT")
            handleSocketConnection(socket)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to GO: ${e.message}")
        }
    }

    private fun handleSocketConnection(socket: Socket) {
        scope?.launch(Dispatchers.IO) {
            try {
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                val hello = ByteArray(33)
                hello[0] = 0x48
                val selfPub = Bytes.unhex(selfEdPubHex)
                System.arraycopy(selfPub, 0, hello, 1, 32)
                output.writeInt(hello.size)
                output.write(hello)
                output.flush()

                val helloLen = input.readInt()
                if (helloLen in 1..512) {
                    val helloMsg = ByteArray(helloLen)
                    input.readFully(helloMsg)
                    if (helloMsg.size >= 33 && helloMsg[0] == 0x48.toByte()) {
                        val peerPubHex = Bytes.hex(helloMsg.copyOfRange(1, 33))
                        val peerKey = "${socket.remoteSocketAddress}"
                        peerSockets[peerKey] = socket
                        peerPubkeys[peerKey] = peerPubHex
                        pubkeyToAddress[peerPubHex] = peerKey
                        Log.d(TAG, "Peer pubkey exchanged: ${peerPubHex.take(16)}...")

                        _incoming.tryEmit(
                            Transport.TransportFrame(
                                senderEdPubHex = peerPubHex,
                                kind = Transport.TransportFrame.KIND_HELLO,
                                payload = ByteArray(0)
                            )
                        )

                        while (running && !socket.isClosed) {
                            try {
                                val msgLen = input.readInt()
                                if (msgLen in 1..65536) {
                                    val msgData = ByteArray(msgLen)
                                    input.readFully(msgData)
                                    _incoming.tryEmit(
                                        Transport.TransportFrame(
                                            senderEdPubHex = peerPubHex,
                                            kind = Transport.TransportFrame.KIND_DATA,
                                            payload = msgData
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket handling error: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
                val peerKey = "${socket.remoteSocketAddress}"
                val pubHex = peerPubkeys.remove(peerKey)
                if (pubHex != null) pubkeyToAddress.remove(pubHex)
                peerSockets.remove(peerKey)
            }
        }
    }

    private fun sendToConnectedPeer(recipientPubHex: String, peerKey: String, payload: ByteArray) {
        val socket = peerSockets[peerKey]
        if (socket != null && !socket.isClosed) {
            try {
                val output = DataOutputStream(socket.getOutputStream())
                output.writeInt(payload.size)
                output.write(payload)
                output.flush()
                Log.d(TAG, "Sent ${payload.size} bytes to ${recipientPubHex.take(16)}")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}")
                peerSockets.remove(peerKey)
                peerPubkeys.remove(peerKey)
                pubkeyToAddress.remove(recipientPubHex)
            }
        }
    }

    private fun connectAndSend(recipientPubHex: String, payload: ByteArray) {
        val goAddr = groupOwnerAddress
        if (goAddr == null && !isGroupOwner) {
            Log.w(TAG, "No WiFi Direct connection, cannot send")
            return
        }
        val target = goAddr ?: "127.0.0.1"
        try {
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(target, SOCKET_PORT), 5000)
            val peerKey = "${socket.remoteSocketAddress}"
            peerSockets[peerKey] = socket

            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            val hello = ByteArray(33)
            hello[0] = 0x48
            val selfPub = Bytes.unhex(selfEdPubHex)
            System.arraycopy(selfPub, 0, hello, 1, 32)
            output.writeInt(hello.size)
            output.write(hello)
            output.flush()

            val helloLen = input.readInt()
            if (helloLen in 1..512) {
                val helloMsg = ByteArray(helloLen)
                input.readFully(helloMsg)
                if (helloMsg.size >= 33 && helloMsg[0] == 0x48.toByte()) {
                    val peerPubHex = Bytes.hex(helloMsg.copyOfRange(1, 33))
                    peerPubkeys[peerKey] = peerPubHex
                    pubkeyToAddress[peerPubHex] = peerKey

                    output.writeInt(payload.size)
                    output.write(payload)
                    output.flush()
                    Log.d(TAG, "Connected and sent ${payload.size} bytes")

                    scope?.launch(Dispatchers.IO) {
                        while (running && !socket.isClosed) {
                            try {
                                val msgLen = input.readInt()
                                if (msgLen in 1..65536) {
                                    val msgData = ByteArray(msgLen)
                                    input.readFully(msgData)
                                    _incoming.tryEmit(
                                        Transport.TransportFrame(
                                            senderEdPubHex = peerPubHex,
                                            kind = Transport.TransportFrame.KIND_DATA,
                                            payload = msgData
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                break
                            }
                        }
                        peerSockets.remove(peerKey)
                        peerPubkeys.remove(peerKey)
                        pubkeyToAddress.remove(peerPubHex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "connectAndSend failed: ${e.message}")
        }
    }
}
