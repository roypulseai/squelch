package com.squelch.app.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/**
 * Wrapper around `Nearby.getConnectionsClient(context)` that implements
 * the spec's `AndroidMeshManager` (§3.3). Strategy is P2P_CLUSTER so that
 * we get the BLE + WiFi Direct fallback for free.
 *
 * Frame callbacks surface to the [Listener] on the engine's thread.
 */
class AndroidMeshManager(private val context: Context) {

    companion object {
        private const val TAG = "AndroidMeshManager"
        const val SERVICE_ID = "com.squelch.app.mesh"
        val STRATEGY = Strategy.P2P_CLUSTER

        /** First kind byte the engine will accept. */
        const val KIND_HELLO: Byte = 0x01
        const val KIND_DATA: Byte = 0x02

        /** Max bytes per byte[] payload. P2P_CLUSTER caps at 32 KiB. */
        const val MAX_PAYLOAD_BYTES = 32_000
    }

    interface Listener {
        /** Called for every frame from any linked peer. */
        fun onFrame(endpointId: String, kind: Byte, payload: ByteArray)
        /** Service-side changes (ad/discovery started, peer connected, lost). */
        fun onEndpointConnected(endpointId: String, info: ConnectionInfo)
        fun onEndpointLost(endpointId: String)
        fun onError(message: String)
    }

    private val client: ConnectionsClient by lazy { Nearby.getConnectionsClient(context) }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept: every Android device in the cluster has already
            // been "approved" by being physically nearby. v0.5 keeps it
            // open; M7 will require a shared QR code or Noise handshake.
            try {
                client.acceptConnection(endpointId, payloadCallback)
                    .addOnSuccessListener { notify {
                        it.onEndpointConnected(endpointId, info)
                    } }
                    .addOnFailureListener { e ->
                        notify { it.onError("accept failed: ${e.message}") }
                    }
            } catch (e: Exception) {
                notify { it.onError("accept threw: ${e.message}") }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val ok = result.status.statusCode == ConnectionsStatusCodes.STATUS_OK
            if (ok) connectedEndpoints.add(endpointId) else synchronized(connectedEndpoints) {
                if (connectedEndpoints.remove(endpointId)) {}
            }
            if (!ok) notify {
                it.onError("connection to $endpointId failed status=${result.status.statusCode}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            synchronized(connectedEndpoints) { connectedEndpoints.remove(endpointId) }
            notify { it.onEndpointLost(endpointId) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            if (bytes.isEmpty()) return
            val kind = bytes[0]
            val body = bytes.copyOfRange(1, bytes.size)
            notify { it.onFrame(endpointId, kind, body) }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            // log only
            Log.v(TAG, "transfer $endpointId status=${update.status} bytes=${update.bytesTransferred}")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            try {
                client.requestConnection(localEndpointName(), endpointId, connectionCallback)
                    .addOnFailureListener { e ->
                        notify { it.onError("requestConnection failed: ${e.message}") }
                    }
            } catch (e: Exception) {
                notify { it.onError("requestConnection threw: ${e.message}") }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            notify { it.onEndpointLost(endpointId) }
        }
    }

    private var listener: Listener? = null

    private val connectedEndpoints = java.util.concurrent.ConcurrentLinkedQueue<String>()
    fun connectedEndpointIds(): List<String> = connectedEndpoints.toList()

    fun setListener(l: Listener) {
        listener = l
    }

    fun start() {
        val name = localEndpointName()
        val advertising = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val discovery = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        client.startAdvertising(name, SERVICE_ID, connectionCallback, advertising)
            .addOnFailureListener { e ->
                notify { it.onError("advertise failed: ${e.message}") }
            }

        client.startDiscovery(SERVICE_ID, discoveryCallback, discovery)
            .addOnFailureListener { e ->
                notify { it.onError("discover failed: ${e.message}") }
            }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connectedEndpoints.clear()
    }

    /** Send a frame to all currently connected endpoints. */
    fun broadcast(kind: Byte, body: ByteArray) {
        if (body.size + 1 > MAX_PAYLOAD_BYTES) {
            notify { it.onError("broadcast too large: ${body.size + 1} > $MAX_PAYLOAD_BYTES") }
            return
        }
        val frame = ByteArray(1 + body.size).also { b ->
            b[0] = kind
            System.arraycopy(body, 0, b, 1, body.size)
        }
        val payload = Payload.fromBytes(frame)
        val endpoints = connectedEndpointIds()
        if (endpoints.isEmpty()) return
        try {
            client.sendPayload(endpoints, payload)
        } catch (e: Exception) {
            notify { it.onError("sendPayload threw: ${e.message}") }
        }
    }

    private fun notify(block: (Listener) -> Unit) {
        listener?.let(block)
    }

    private fun localEndpointName(): String {
        // Truncated so that the BLE Device.Name is legal (<=17 bytes).
        return "Squelch-app".take(17)
    }
}
