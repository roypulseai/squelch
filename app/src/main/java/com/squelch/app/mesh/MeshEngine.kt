package com.squelch.app.mesh

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the [AndroidMeshManager] and exposes a small StateFlow suitable for
 * the UI's mesh status card. The engine doesn't route application data
 * yet - that's M7 - it manages the transport lifetime and tracks peers.
 *
 * Once M7 lands, the engine grows a payload-routing layer (signed
 * envelopes, TTL-flood queue, store-and-forward) and the [AndroidMeshManager.Listener]
 * becomes a real inbound queue feeding the message layer.
 */
class MeshEngine(context: Context) {

    private val manager = AndroidMeshManager(context)

    private val _status = MutableStateFlow(MeshStatus())
    val status: StateFlow<MeshStatus> = _status.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, MeshPeer>>(emptyMap())
    val peers: StateFlow<Map<String, MeshPeer>> = _peers.asStateFlow()

    private val listener = object : AndroidMeshManager.Listener {
        override fun onFrame(endpointId: String, kind: Byte, payload: ByteArray) {
            // Reserved for M7 (pay-loaded frames routed into the message layer).
        }

        override fun onEndpointConnected(
            endpointId: String,
            info: com.google.android.gms.nearby.connection.ConnectionInfo
        ) {
            _peers.value = _peers.value.toMutableMap().apply {
                put(endpointId, MeshPeer(endpointId, info.endpointName, System.currentTimeMillis()))
            }
            publishStatus()
        }

        override fun onEndpointLost(endpointId: String) {
            _peers.value = _peers.value.toMutableMap().apply { remove(endpointId) }
            publishStatus()
        }

        override fun onError(message: String) {
            _status.value = _status.value.copy(lastError = message)
        }
    }

    fun start() {
        if (_status.value.running) return
        manager.setListener(listener)
        manager.start()
        _status.value = _status.value.copy(
            running = true,
            lastError = null,
            startedAt = System.currentTimeMillis()
        )
    }

    fun stop() {
        if (!_status.value.running) return
        manager.stop()
        _status.value = _status.value.copy(running = false)
        _peers.value = emptyMap()
        publishStatus()
    }

    private fun publishStatus() {
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
}
