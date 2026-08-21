package com.squelch.app.ui.screens.mesh

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.squelch.app.auth.AuthRepository
import com.squelch.app.crypto.Identity
import com.squelch.app.data.local.entity.ContactEntity
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.mesh.engine.MeshEngine
import com.squelch.app.mesh.engine.MeshEngineManager
import com.squelch.app.mesh.transport.WifiDirectManager
import com.squelch.app.util.toHex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RadarViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
    private val vaultRepository: VaultRepository,
    private val meshEngineManager: MeshEngineManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RadarViewModel"
    }

    private val meshEngine: MeshEngine? get() = meshEngineManager.get()

    data class TransportStatus(
        val name: String,
        val icon: String,
        val isEnabled: Boolean,
        val isActive: Boolean,
        val peerCount: Int,
        val description: String
    )

    data class PeerInfo(
        val id: String,
        val name: String,
        val transport: String,
        val signalStrength: Int,
        val lastSeen: Long,
        val isContact: Boolean
    )

    data class SquelchUser(
        val uid: String,
        val email: String,
        val displayName: String,
        val edPub: String,
        val isContact: Boolean
    )

    data class NetworkStats(
        val totalPeers: Int,
        val blePeers: Int,
        val wifiDirectPeers: Int,
        val internetRelay: Boolean,
        val uptimeMs: Long,
        val messagesRelayed: Int
    )

    private val wifiDirectManager = WifiDirectManager(application)

    private val _transports = MutableStateFlow<List<TransportStatus>>(emptyList())
    val transports: StateFlow<List<TransportStatus>> = _transports.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private val _stats = MutableStateFlow(NetworkStats(0, 0, 0, false, 0, 0))
    val stats: StateFlow<NetworkStats> = _stats.asStateFlow()

    private val _selfPubkey = MutableStateFlow("")
    val selfPubkey: StateFlow<String> = _selfPubkey.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _bleEnabled = MutableStateFlow(true)
    val bleEnabled: StateFlow<Boolean> = _bleEnabled.asStateFlow()

    private val _wifiDirectEnabled = MutableStateFlow(true)
    val wifiDirectEnabled: StateFlow<Boolean> = _wifiDirectEnabled.asStateFlow()

    private val _squelchUsers = MutableStateFlow<List<SquelchUser>>(emptyList())
    val squelchUsers: StateFlow<List<SquelchUser>> = _squelchUsers.asStateFlow()

    private var startTime = System.currentTimeMillis()
    private var messageCount = 0

    init {
        startTime = System.currentTimeMillis()
        initSelfPubkey()
        meshEngineManager.getOrCreate()
        wifiDirectManager.start()

        observePeerChanges()
        startTransportPolling()
        loadSquelchUsers()

        viewModelScope.launch {
            delay(2000)
            if (_wifiDirectEnabled.value) {
                wifiDirectManager.discoverPeers()
            }
        }
    }

    private fun initSelfPubkey() {
        val googleUid = authRepository.signedIn()?.googleUid ?: return
        val identity = Identity.fromGoogleUid(googleUid)
        _selfPubkey.value = identity.edPub.toHex()
    }

    private fun loadSquelchUsers() {
        viewModelScope.launch {
            val selfUid = authRepository.signedIn()?.googleUid ?: return@launch
            val db = vaultRepository.db

            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("users")
                    .limit(50)
                    .get()
                    .await()

                val contactUids = withContext(Dispatchers.IO) {
                    try {
                        db?.contacts()?.firebaseUids() ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }.toSet()

                val users = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == selfUid) return@mapNotNull null
                    val email = doc.getString("email") ?: return@mapNotNull null
                    val displayName = doc.getString("displayName") ?: email.substringBefore("@")
                    val edPub = doc.getString("edPub") ?: ""
                    val isContact = uid in contactUids

                    SquelchUser(
                        uid = uid,
                        email = email,
                        displayName = displayName,
                        edPub = edPub,
                        isContact = isContact
                    )
                }
                Log.d(TAG, "Loaded ${users.size} Squelch users from Firestore")
                _squelchUsers.value = users
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Squelch users: ${e.message}")
                _squelchUsers.value = emptyList()
            }
        }
    }

    fun refreshSquelchUsers() {
        loadSquelchUsers()
    }

    private fun observePeerChanges() {
        viewModelScope.launch {
            val wifiFlow = wifiDirectManager.peers

            while (isActive) {
                val engine = meshEngine
                val bleFlow = engine?.peers
                if (bleFlow != null) {
                    bleFlow.combine(wifiFlow) { blePeers, wifiPeers ->
                        Pair(blePeers, wifiPeers)
                    }.collect { (blePeers, wifiPeers) ->
                        rebuildPeerList(blePeers, wifiPeers)
                    }
                } else {
                    wifiFlow.collect { wifiPeers ->
                        rebuildPeerList(emptySet(), wifiPeers)
                    }
                }
            }
        }
    }

    private suspend fun rebuildPeerList(
        blePeers: Set<String>,
        wifiPeers: List<WifiDirectManager.WifiDirectPeer>
    ) {
        val db = vaultRepository.db
        val contactPubkeys = try {
            withContext(Dispatchers.IO) {
                db?.contacts()?.pubkeys()?.toSet() ?: emptySet()
            }
        } catch (_: Exception) { emptySet() }

        val peerList = mutableListOf<PeerInfo>()
        val seen = mutableSetOf<String>()

        if (_bleEnabled.value) {
            for (pubkey in blePeers) {
                if (pubkey == _selfPubkey.value) continue
                if (!seen.add(pubkey)) continue
                peerList.add(
                    PeerInfo(
                        id = pubkey,
                        name = pubkey.take(8),
                        transport = "BLE",
                        signalStrength = 75,
                        lastSeen = System.currentTimeMillis(),
                        isContact = pubkey in contactPubkeys
                    )
                )
            }
        }

        if (_wifiDirectEnabled.value) {
            for (peer in wifiPeers) {
                if (!seen.add(peer.deviceAddress)) continue
                peerList.add(
                    PeerInfo(
                        id = peer.deviceAddress,
                        name = peer.deviceName,
                        transport = "Wi-Fi Direct",
                        signalStrength = if (peer.status == "Connected") 95 else 50,
                        lastSeen = System.currentTimeMillis(),
                        isContact = false
                    )
                )
            }
        }

        _peers.value = peerList
    }

    private fun startTransportPolling() {
        viewModelScope.launch {
            while (isActive) {
                updateTransportStatus()
                updateStats()
                delay(3000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateTransportStatus() {
        val ctx = getApplication<Application>()

        val btManager = ctx.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btEnabled = btManager?.adapter?.isEnabled == true

        val wifiManager = ctx.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val wifiEnabled = wifiManager?.isWifiEnabled == true

        val transports = mutableListOf<TransportStatus>()

        transports.add(
            TransportStatus(
                name = "Internet",
                icon = "\uD83C\uDF10",
                isEnabled = true,
                isActive = meshEngine != null,
                peerCount = 0,
                description = "Firebase relay for global reach"
            )
        )

        val bleActive = _bleEnabled.value && btEnabled && meshEngine != null
        transports.add(
            TransportStatus(
                name = "Bluetooth LE",
                icon = "\uD83D\uDCE1",
                isEnabled = _bleEnabled.value && btEnabled,
                isActive = bleActive,
                peerCount = if (bleActive) _peers.value.count { it.transport == "BLE" } else 0,
                description = if (!btEnabled) "Bluetooth is off"
                    else if (!_bleEnabled.value) "Disabled by user"
                    else if (meshEngine == null) "Engine not initialized"
                    else "Local mesh discovery"
            )
        )

        val wifiDirectEnabled = wifiDirectManager.isEnabled.value
        val wdActive = _wifiDirectEnabled.value && wifiDirectEnabled
        transports.add(
            TransportStatus(
                name = "Wi-Fi Direct",
                icon = "\uD83D\uDCF6",
                isEnabled = wdActive,
                isActive = wdActive,
                peerCount = if (wdActive) _peers.value.count { it.transport == "Wi-Fi Direct" } else 0,
                description = if (!wifiEnabled) "Wi-Fi is off"
                    else if (!wifiDirectEnabled) "Wi-Fi P2P not supported"
                    else if (!_wifiDirectEnabled.value) "Disabled by user"
                    else "High-speed local transfer"
            )
        )

        _transports.value = transports
    }

    private fun updateStats() {
        val currentPeers = _peers.value
        _stats.value = NetworkStats(
            totalPeers = currentPeers.size,
            blePeers = currentPeers.count { it.transport == "BLE" },
            wifiDirectPeers = currentPeers.count { it.transport == "Wi-Fi Direct" },
            internetRelay = meshEngine != null,
            uptimeMs = System.currentTimeMillis() - startTime,
            messagesRelayed = messageCount
        )
    }

    fun toggleBle() {
        _bleEnabled.value = !_bleEnabled.value
        updateTransportStatus()

        if (!_bleEnabled.value) {
            _peers.value = _peers.value.filter { it.transport != "BLE" }
            updateStats()
        }
    }

    fun toggleWifiDirect() {
        _wifiDirectEnabled.value = !_wifiDirectEnabled.value

        if (_wifiDirectEnabled.value) {
            wifiDirectManager.discoverPeers()
        } else {
            _peers.value = _peers.value.filter { it.transport != "Wi-Fi Direct" }
            updateStats()
        }

        updateTransportStatus()
    }

    fun scanNow() {
        _isScanning.value = true
        viewModelScope.launch {
            if (_wifiDirectEnabled.value) {
                wifiDirectManager.discoverPeers()
            }
            delay(3000)
            _isScanning.value = false
            updateStats()
        }
    }

    fun incrementMessageCount() {
        messageCount++
        updateStats()
    }

    override fun onCleared() {
        super.onCleared()
        wifiDirectManager.stop()
    }}
