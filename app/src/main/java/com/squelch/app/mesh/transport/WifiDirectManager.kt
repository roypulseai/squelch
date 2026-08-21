package com.squelch.app.mesh.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WifiDirectManager(private val context: Context) {

    companion object {
        private const val TAG = "WifiDirectManager"
    }

    data class WifiDirectPeer(
        val deviceName: String,
        val deviceAddress: String,
        val status: String,
        val isAvailable: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var registering = false

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _peers = MutableStateFlow<List<WifiDirectPeer>>(emptyList())
    val peers: StateFlow<List<WifiDirectPeer>> = _peers.asStateFlow()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    fun start() {
        if (registering) return
        registering = true

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
                        _isEnabled.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        Log.d(TAG, "Wi-Fi P2P state: enabled=${_isEnabled.value}")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        requestPeers()
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        Log.d(TAG, "Connection changed")
                    }
                }
            }
        }

        try {
            context.registerReceiver(receiver, intentFilter)
            _isEnabled.value = true
            Log.d(TAG, "Wi-Fi Direct manager started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver: ${e.message}")
            _isEnabled.value = false
        }
    }

    fun stop() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        receiver = null
        channel = null
        manager = null
        registering = false
        _isEnabled.value = false
        _isDiscovering.value = false
        _peers.value = emptyList()
        _peerCount.value = 0
        scope.cancel()
        Log.d(TAG, "Wi-Fi Direct manager stopped")
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!_isEnabled.value) return

        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = true
                Log.d(TAG, "Peer discovery started")
            }
            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                Log.e(TAG, "Peer discovery failed: reason=$reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val mgr = manager ?: return
        val ch = channel ?: return

        mgr.requestPeers(ch) { peerList: WifiP2pDeviceList ->
            val peerDevices = peerList.deviceList.map { device ->
                WifiDirectPeer(
                    deviceName = device.deviceName.ifEmpty { "Unknown Device" },
                    deviceAddress = device.deviceAddress,
                    status = when (device.status) {
                        0 -> "Available"
                        1 -> "Invited"
                        2 -> "Connected"
                        3 -> "Failed"
                        4 -> "Unavailable"
                        else -> "Unknown"
                    },
                    isAvailable = device.status == 0
                )
            }
            _peers.value = peerDevices
            _peerCount.value = peerDevices.size
            Log.d(TAG, "Found ${peerDevices.size} Wi-Fi Direct peers")
        }
    }
}
