package com.squelch.app.mesh.transport

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.squelch.app.util.Bytes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val selfEdPubHex: String
) : Transport {

    companion object {
        private const val TAG = "BleTransport"
        val SERVICE_UUID: UUID = UUID.fromString("6b5b17a0-e4f8-4e4e-a0b4-f2c5d1e8f900")
        val PUBKEY_CHAR_UUID: UUID = UUID.fromString("a5c117a1-e4f8-4e4e-a0b4-f2c5d1e8f901")
        val MSG_CHAR_UUID: UUID = UUID.fromString("b5c217a2-e4f8-4e4e-a0b4-f2c5d1e8f902")
        private const val MAX_MTU = 512
        private const val HEADER_SIZE = 37
        private const val MAX_CHUNK = MAX_MTU - HEADER_SIZE
        private const val MAX_CONNECTIONS = 8
        private const val WRITE_TIMEOUT_MS = 3_000L
        private const val SCAN_WINDOW_MS = 10_000L
        private const val SCAN_PAUSE_MS = 5_000L
        private const val ADVERTISE_RETRY_MS = 30_000L
        private const val STORE_FORWARD_TTL_MS = 30_000L
        private const val SEEN_SET_SIZE = 1000
    }

    override val name: String = "BLE"

    private val _incoming = MutableSharedFlow<Transport.TransportFrame>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<Transport.TransportFrame> = _incoming.asSharedFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    private var gattServer: BluetoothGattServer? = null
    @Volatile private var running = false
    private var scope: CoroutineScope? = null

    private val devicePubkeys = ConcurrentHashMap<String, String>()
    private val pubkeyDevices = ConcurrentHashMap<String, String>()

    private val writeQueue = ConcurrentLinkedQueue<WriteJob>()
    @Volatile private var isWriting = false

    private val activeConnections = AtomicInteger(0)
    private val pendingConnections = ConcurrentHashMap<String, Boolean>()

    private val fragmentBuffers = ConcurrentHashMap<String, FragmentBuffer>()

    private val seenMessages = object : LinkedHashMap<String, Long>(SEEN_SET_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > SEEN_SET_SIZE
        }
    }
    private val seenMessagesLock = Any()

    private val pendingMessages = ConcurrentHashMap<String, MutableList<PendingMessage>>()

    private var scanWatchdogJob: Job? = null
    private var advertiseRetryJob: Job? = null

    private val selfEdPub: ByteArray get() = Bytes.unhex(selfEdPubHex)
    private val selfPubkeyHash: ByteArray by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(selfEdPub).copyOfRange(0, 8)
    }

    private data class WriteJob(
        val deviceAddress: String,
        val data: ByteArray,
        val kind: Int,
        val onResult: (Boolean) -> Unit
    )

    private data class FragmentBuffer(
        val fragments: MutableMap<Int, ByteArray> = mutableMapOf(),
        var expectedCount: Int = 0,
        var receivedCount: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    private data class PendingMessage(
        val recipientPubHex: String,
        val data: ByteArray,
        val kind: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    override fun start() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = btManager?.adapter
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        running = true
        startGattServer()
        startAdvertising()
        startScanning()
        startScanWatchdog()
        startAdvertiseRetry()
        Log.d(TAG, "BLE transport started, pubkey=${selfEdPubHex.take(16)}...")
    }

    override fun stop() {
        running = false
        stopAdvertising()
        stopScanning()
        stopGattServer()
        scanWatchdogJob?.cancel()
        advertiseRetryJob?.cancel()
        scope?.cancel()
        scope = null
        writeQueue.clear()
        isWriting = false
        devicePubkeys.clear()
        pubkeyDevices.clear()
        fragmentBuffers.clear()
        pendingConnections.clear()
        pendingMessages.clear()
        synchronized(seenMessagesLock) { seenMessages.clear() }
        Log.d(TAG, "BLE transport stopped")
    }

    override fun send(recipientEdPubHex: String, payload: ByteArray, kind: Int) {
        if (!running) return

        if (recipientEdPubHex.isEmpty()) {
            for ((addr, _) in pubkeyDevices) {
                enqueueWrite(addr, payload, kind)
            }
            return
        }

        val deviceAddress = pubkeyDevices[recipientEdPubHex]
        if (deviceAddress == null) {
            pendingMessages.getOrPut(recipientEdPubHex) { mutableListOf() }
                .add(PendingMessage(recipientEdPubHex, payload, kind))
            Log.d(TAG, "Queued message for offline peer ${recipientEdPubHex.take(16)}")
            return
        }
        enqueueWrite(deviceAddress, payload, kind)
    }

    private fun enqueueWrite(deviceAddress: String, payload: ByteArray, kind: Int) {
        writeQueue.offer(WriteJob(deviceAddress, payload, kind) { success ->
            if (!success) {
                Log.w(TAG, "Write failed for $deviceAddress, retrying in 2s")
                scope?.launch {
                    delay(2000)
                    if (running) enqueueWrite(deviceAddress, payload, kind)
                }
            }
        })
        processWriteQueue()
    }

    private fun processWriteQueue() {
        if (isWriting) return
        val job = writeQueue.poll() ?: return
        isWriting = true
        scope?.launch {
            try {
                connectAndWrite(job)
            } catch (e: Exception) {
                Log.e(TAG, "Write job failed: ${e.message}")
                job.onResult(false)
            } finally {
                isWriting = false
                processWriteQueue()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectAndWrite(job: WriteJob) {
        if (activeConnections.get() >= MAX_CONNECTIONS) {
            Log.w(TAG, "Max connections ($MAX_CONNECTIONS) reached, queuing write")
            delay(500)
            writeQueue.offer(job)
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(job.deviceAddress) ?: return
        val connected = CompletableDeferred<Boolean>()
        val writeResult = CompletableDeferred<Boolean>()

        val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        activeConnections.incrementAndGet()
                        try { g.discoverServices() } catch (_: Exception) {}
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (activeConnections.get() > 0) activeConnections.decrementAndGet()
                        try { g.close() } catch (_: Exception) {}
                        if (!connected.isCompleted) connected.complete(false)
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    try { g.disconnect() } catch (_: Exception) {}
                    connected.complete(false)
                    return
                }
                try { g.requestMtu(MAX_MTU) } catch (_: Exception) {}
                connected.complete(true)
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU negotiated: $mtu")
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                writeResult.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        })

        val ready = withTimeoutOrNull(WRITE_TIMEOUT_MS) { connected.await() } ?: false
        if (!ready) {
            try { gatt?.close() } catch (_: Exception) {}
            job.onResult(false)
            return
        }

        val svc = gatt?.getService(SERVICE_UUID)
        val msgChar = svc?.getCharacteristic(MSG_CHAR_UUID)
        if (msgChar == null) {
            try { gatt?.disconnect() } catch (_: Exception) {}
            job.onResult(false)
            return
        }

        val payload = job.data
        val chunks = fragmentPayload(payload, job.kind)

        for (chunk in chunks) {
            msgChar.value = chunk
            msgChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            try {
                gatt?.writeCharacteristic(msgChar)
                val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) { writeResult.await() } ?: false
                if (!result) {
                    Log.e(TAG, "Chunk write failed")
                    job.onResult(false)
                    try { gatt?.disconnect() } catch (_: Exception) {}
                    return
                }
                delay(20)
            } catch (e: Exception) {
                Log.e(TAG, "Write exception: ${e.message}")
                job.onResult(false)
                try { gatt?.disconnect() } catch (_: Exception) {}
                return
            }
        }

        job.onResult(true)
        try { gatt?.disconnect() } catch (_: Exception) {}
        Log.d(TAG, "Sent ${payload.size} bytes in ${chunks.size} chunks to ${job.deviceAddress}")
    }

    private fun fragmentPayload(payload: ByteArray, kind: Int): List<ByteArray> {
        if (payload.size <= MAX_CHUNK) {
            return listOf(buildFrame(payload, 1, 0, kind))
        }

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        var index = 0
        val total = (payload.size + MAX_CHUNK - 1) / MAX_CHUNK

        while (offset < payload.size) {
            val size = minOf(MAX_CHUNK, payload.size - offset)
            val chunk = payload.copyOfRange(offset, offset + size)
            chunks.add(buildFrame(chunk, total, index, kind))
            offset += size
            index++
        }
        return chunks
    }

    private fun buildFrame(chunk: ByteArray, totalChunks: Int, index: Int, kind: Int): ByteArray {
        val frame = ByteArray(HEADER_SIZE + chunk.size)
        System.arraycopy(selfEdPub, 0, frame, 0, 32)
        frame[32] = kind.toByte()
        frame[33] = (totalChunks and 0xFF).toByte()
        frame[34] = ((totalChunks shr 8) and 0xFF).toByte()
        frame[35] = (index and 0xFF).toByte()
        frame[36] = ((index shr 8) and 0xFF).toByte()
        System.arraycopy(chunk, 0, frame, HEADER_SIZE, chunk.size)
        return frame
    }

    // --- Advertising ---

    private fun startAdvertising() {
        val adapter = bluetoothAdapter ?: return
        advertiser = adapter.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "No BluetoothLeAdvertiser available")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), selfPubkeyHash)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Advertising started")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed: $errorCode")
                advertiseCallback = null
            }
        }
        try {
            advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "startAdvertising crashed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        advertiseCallback?.let { cb ->
            try { advertiser?.stopAdvertising(cb) } catch (_: Exception) {}
        }
        advertiseCallback = null
    }

    // --- Scanning with duty cycle ---

    private fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        scanner = adapter.bluetoothLeScanner ?: return

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val device = result.device ?: return
                val serviceData = result.scanRecord?.serviceData?.get(ParcelUuid(SERVICE_UUID))
                if (serviceData != null && serviceData.size >= 8) {
                    val hash = serviceData.copyOfRange(0, 8)
                    val hashHex = Bytes.hex(hash)
                    scope?.launch { exchangePubkeys(device.address, hashHex) }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner?.startScan(filters, settings, scanCallback)
            Log.d(TAG, "Scanning started")
        } catch (e: Exception) {
            Log.e(TAG, "startScan crashed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        scanCallback?.let { cb ->
            try { scanner?.stopScan(cb) } catch (_: Exception) {}
        }
        scanCallback = null
    }

    // --- Self-healing watchdog ---

    private fun startScanWatchdog() {
        scanWatchdogJob = scope?.launch {
            while (running) {
                delay(SCAN_WINDOW_MS)
                if (!running) break
                stopScanning()
                delay(SCAN_PAUSE_MS)
                if (running) startScanning()
            }
        }
    }

    private fun startAdvertiseRetry() {
        advertiseRetryJob = scope?.launch {
            while (running) {
                delay(ADVERTISE_RETRY_MS)
                if (!running) break
                if (advertiseCallback == null) {
                    Log.d(TAG, "Retrying advertising...")
                    startAdvertising()
                }
            }
        }
    }

    // --- Pubkey exchange ---

    @SuppressLint("MissingPermission")
    private fun exchangePubkeys(deviceAddress: String, hashHex: String) {
        if (devicePubkeys.containsKey(deviceAddress)) {
            deliverPendingMessages(deviceAddress)
            return
        }
        if (pendingConnections.putIfAbsent(deviceAddress, true) != null) return
        if (activeConnections.get() >= MAX_CONNECTIONS) {
            pendingConnections.remove(deviceAddress)
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        var gatt: BluetoothGatt? = null
        try {
            gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            activeConnections.incrementAndGet()
                            try { g.discoverServices() } catch (_: Exception) {}
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (activeConnections.get() > 0) activeConnections.decrementAndGet()
                            pendingConnections.remove(deviceAddress)
                            try { g.close() } catch (_: Exception) {}
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        try { g.disconnect() } catch (_: Exception) {}
                        return
                    }
                    try { g.requestMtu(MAX_MTU) } catch (_: Exception) {}
                    val char = g.getService(SERVICE_UUID)?.getCharacteristic(PUBKEY_CHAR_UUID)
                    if (char != null) {
                        try { g.readCharacteristic(char) } catch (_: Exception) {}
                    } else {
                        try { g.disconnect() } catch (_: Exception) {}
                    }
                }

                override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                    Log.d(TAG, "MTU negotiated: $mtu")
                }

                override fun onCharacteristicRead(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == PUBKEY_CHAR_UUID && value.size == 32) {
                        val peerPubHex = Bytes.hex(value)
                        devicePubkeys[deviceAddress] = peerPubHex
                        pubkeyDevices[peerPubHex] = deviceAddress
                        Log.d(TAG, "Exchanged pubkey for ${device.address}: ${peerPubHex.take(16)}...")
                        _incoming.tryEmit(
                            Transport.TransportFrame(
                                senderEdPubHex = peerPubHex,
                                kind = Transport.TransportFrame.KIND_HELLO,
                                payload = ByteArray(0)
                            )
                        )
                        deliverPendingMessages(deviceAddress)
                    }
                    try { g.disconnect() } catch (_: Exception) {}
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Pubkey exchange failed: ${e.message}")
            pendingConnections.remove(deviceAddress)
            try { gatt?.close() } catch (_: Exception) {}
        }
    }

    // --- Store-and-forward ---

    private fun deliverPendingMessages(deviceAddress: String) {
        val peerPubHex = devicePubkeys[deviceAddress] ?: return
        val pending = pendingMessages.remove(peerPubHex) ?: return
        val now = System.currentTimeMillis()
        for (msg in pending) {
            if (now - msg.timestamp < STORE_FORWARD_TTL_MS) {
                Log.d(TAG, "Delivering stored message to ${peerPubHex.take(16)}")
                enqueueWrite(deviceAddress, msg.data, msg.kind)
            }
        }
    }

    // --- GATT Server ---

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        try {
            gattServer = btManager.openGattServer(context, gattServerCallback)
            if (gattServer == null) {
                Log.e(TAG, "GATT server is null — openGattServer returned null")
                return
            }
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val pubkeyChar = BluetoothGattCharacteristic(
                PUBKEY_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            pubkeyChar.value = selfEdPub
            service.addCharacteristic(pubkeyChar)

            val msgChar = BluetoothGattCharacteristic(
                MSG_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(msgChar)

            val added = gattServer?.addService(service)
            if (added != true) {
                Log.e(TAG, "GATT addService failed")
            } else {
                Log.d(TAG, "GATT server started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "GATT server start failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopGattServer() {
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid != MSG_CHAR_UUID || value.size < HEADER_SIZE) {
                if (responseNeeded) {
                    try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0)) } catch (_: Exception) {}
                }
                return
            }

            val senderPubHex = Bytes.hex(value.copyOfRange(0, 32))
            devicePubkeys[device.address] = senderPubHex
            pubkeyDevices[senderPubHex] = device.address

            val kind = value[32].toInt() and 0xFF
            val totalChunks = ((value[33].toInt() and 0xFF)) or ((value[34].toInt() and 0xFF) shl 8)
            val chunkIndex = ((value[35].toInt() and 0xFF)) or ((value[36].toInt() and 0xFF) shl 8)
            val chunkData = value.copyOfRange(HEADER_SIZE, value.size)

            val contentHash = MessageDigest.getInstance("SHA-256").digest(chunkData).copyOfRange(0, 8)
            val msgId = "${senderPubHex}_${Bytes.hex(contentHash)}"
            synchronized(seenMessagesLock) {
                if (seenMessages.containsKey(msgId)) {
                    if (responseNeeded) {
                        try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0)) } catch (_: Exception) {}
                    }
                    return
                }
                seenMessages[msgId] = System.currentTimeMillis()
            }

            if (totalChunks <= 1) {
                _incoming.tryEmit(
                    Transport.TransportFrame(
                        senderEdPubHex = senderPubHex,
                        kind = kind,
                        payload = chunkData
                    )
                )
            } else {
                val key = "${senderPubHex}_${totalChunks}"
                val buffer = fragmentBuffers.getOrPut(key) { FragmentBuffer() }
                buffer.expectedCount = totalChunks
                buffer.fragments[chunkIndex] = chunkData
                buffer.receivedCount = buffer.fragments.size

                if (buffer.receivedCount >= buffer.expectedCount) {
                    val sorted = (0 until totalChunks).mapNotNull { buffer.fragments[it] }
                    if (sorted.size == totalChunks) {
                        val assembled = sorted.reduce { acc, bytes -> acc + bytes }
                        _incoming.tryEmit(
                            Transport.TransportFrame(
                                senderEdPubHex = senderPubHex,
                                kind = kind,
                                payload = assembled
                            )
                        )
                        fragmentBuffers.remove(key)
                    }
                }

                if (System.currentTimeMillis() - buffer.timestamp > 30_000) {
                    fragmentBuffers.remove(key)
                }
            }

            if (responseNeeded) {
                try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0)) } catch (_: Exception) {}
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == PUBKEY_CHAR_UUID) {
                try {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, selfEdPub)
                } catch (_: Exception) {}
            }
        }
    }
}
