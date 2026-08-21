package com.squelch.app.mesh.transport

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.squelch.app.util.Bytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
        private const val MAX_CHUNK = 180
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
    private val chunkBuffers = ConcurrentHashMap<String, MutableList<ByteArray>>()
    private val chunkTotals = ConcurrentHashMap<String, Int>()
    private val pendingConnections = ConcurrentHashMap<String, Boolean>()

    private val selfEdPub: ByteArray get() = Bytes.unhex(selfEdPubHex)
    private val selfPubkeyHash: ByteArray by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(selfEdPub).copyOfRange(0, 8)
    }

    override fun start() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = btManager?.adapter
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        running = true
        startAdvertising()
        startScanning()
        startGattServer()
        Log.d(TAG, "BLE transport started, pubkey=${selfEdPubHex.take(16)}...")
    }

    override fun stop() {
        running = false
        stopAdvertising()
        stopScanning()
        stopGattServer()
        scope?.cancel()
        scope = null
        devicePubkeys.clear()
        pubkeyDevices.clear()
        chunkBuffers.clear()
        chunkTotals.clear()
        pendingConnections.clear()
        Log.d(TAG, "BLE transport stopped")
    }

    override fun send(recipientEdPubHex: String, payload: ByteArray) {
        if (!running) return
        val deviceAddress = pubkeyDevices[recipientEdPubHex]
        if (deviceAddress == null) {
            Log.w(TAG, "No known device for pubkey ${recipientEdPubHex.take(16)}, cannot send via BLE")
            return
        }
        scope?.launch {
            sendViaGatt(deviceAddress, recipientEdPubHex, payload)
        }
    }

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
                Log.d(TAG, "Advertising started (connectable)")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed: $errorCode")
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
                    Log.d(TAG, "Found Squelch peer: ${device.address} hash=${hashHex}")
                    scope?.launch {
                        exchangePubkeys(device.address, hashHex)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
            }
        }

        val filters = listOf(
            android.bluetooth.le.ScanFilter.Builder()
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

    @SuppressLint("MissingPermission")
    private fun exchangePubkeys(deviceAddress: String, hashHex: String) {
        if (devicePubkeys.containsKey(deviceAddress)) return
        if (pendingConnections.putIfAbsent(deviceAddress, true) != null) return

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        var gatt: BluetoothGatt? = null
        try {
            gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothGattServer.STATE_CONNECTED) {
                        try { g.discoverServices() } catch (_: Exception) {}
                    } else if (newState == BluetoothGattServer.STATE_DISCONNECTED) {
                        try { g.close() } catch (_: Exception) {}
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        try { g.disconnect() } catch (_: Exception) {}
                        return
                    }
                    val char = g.getService(SERVICE_UUID)?.getCharacteristic(PUBKEY_CHAR_UUID)
                    if (char != null) {
                        try { g.readCharacteristic(char) } catch (_: Exception) {}
                    } else {
                        try { g.disconnect() } catch (_: Exception) {}
                    }
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
                    }
                    try { g.disconnect() } catch (_: Exception) {}
                }
            })
            gatt?.let { g ->
                try { g.requestMtu(512) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pubkey exchange failed: ${e.message}")
            try { gatt?.close() } catch (_: Exception) {}
        } finally {
            pendingConnections.remove(deviceAddress)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendViaGatt(deviceAddress: String, recipientPubHex: String, payload: ByteArray) {
        if (pendingConnections.putIfAbsent(deviceAddress, true) != null) return
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        var gatt: BluetoothGatt? = null
        try {
            gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try { g.discoverServices() } catch (_: Exception) {}
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        try { g.close() } catch (_: Exception) {}
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        try { g.disconnect() } catch (_: Exception) {}
                        return
                    }
                    val svc = g.getService(SERVICE_UUID) ?: run {
                        try { g.disconnect() } catch (_: Exception) {}
                        return
                    }
                    val msgChar = svc.getCharacteristic(MSG_CHAR_UUID) ?: run {
                        try { g.disconnect() } catch (_: Exception) {}
                        return
                    }
                    writeChunked(g, msgChar, payload)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Send via GATT failed: ${e.message}")
            try { gatt?.close() } catch (_: Exception) {}
        } finally {
            pendingConnections.remove(deviceAddress)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeChunked(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, payload: ByteArray) {
        val senderPub = selfEdPub
        if (payload.size <= MAX_CHUNK - 4) {
            val frame = ByteArray(32 + 1 + 2 + 2 + payload.size)
            System.arraycopy(senderPub, 0, frame, 0, 32)
                    frame[32] = Transport.TransportFrame.KIND_DATA.toByte()
                    frame[33] = 0x01
                    frame[34] = 0x00
                    System.arraycopy(payload, 0, frame, 37, payload.size)
                    try {
                        char.value = frame
                        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        gatt.writeCharacteristic(char)
                        Log.d(TAG, "Sent ${payload.size} bytes via GATT")
                    } catch (e: Exception) {
                        Log.e(TAG, "GATT write failed: ${e.message}")
                    }
                    try { gatt.disconnect() } catch (_: Exception) {}
                    return
                }

                val totalChunks = (payload.size + MAX_CHUNK - 37 - 1) / (MAX_CHUNK - 37)
                var offset = 0
                var chunkIndex = 0
                while (offset < payload.size) {
                    val chunkSize = minOf(MAX_CHUNK - 37, payload.size - offset)
                    val frame = ByteArray(32 + 1 + 2 + 2 + chunkSize)
                    System.arraycopy(senderPub, 0, frame, 0, 32)
                    frame[32] = Transport.TransportFrame.KIND_DATA.toByte()
            frame[33] = (totalChunks and 0xFF).toByte()
            frame[34] = ((totalChunks shr 8) and 0xFF).toByte()
            frame[35] = (chunkIndex and 0xFF).toByte()
            frame[36] = ((chunkIndex shr 8) and 0xFF).toByte()
            System.arraycopy(payload, offset, frame, 37, chunkSize)
            try {
                char.value = frame
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(char)
                Thread.sleep(20)
            } catch (e: Exception) {
                Log.e(TAG, "Chunk write failed: ${e.message}")
                break
            }
            offset += chunkSize
            chunkIndex++
        }
        try { gatt.disconnect() } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        try {
            gattServer = btManager.openGattServer(context, gattServerCallback)
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

            gattServer?.addService(service)
            Log.d(TAG, "GATT server started")
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
            if (characteristic.uuid != MSG_CHAR_UUID || value.size < 37) {
                if (responseNeeded) {
                    try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0)) } catch (_: Exception) {}
                }
                return
            }

            val senderPubHex = Bytes.hex(value.copyOfRange(0, 32))
            devicePubkeys[device.address] = senderPubHex
            pubkeyDevices[senderPubHex] = device.address

            val totalChunks = ((value[33].toInt() and 0xFF)) or ((value[34].toInt() and 0xFF) shl 8)
            val chunkIndex = ((value[35].toInt() and 0xFF)) or ((value[36].toInt() and 0xFF) shl 8)
            val chunkData = value.copyOfRange(37, value.size)

            if (totalChunks <= 1) {
                _incoming.tryEmit(
                    Transport.TransportFrame(
                        senderEdPubHex = senderPubHex,
                        kind = Transport.TransportFrame.KIND_DATA,
                        payload = chunkData
                    )
                )
            } else {
                val key = "${senderPubHex}_$requestId"
                val buffer = chunkBuffers.getOrPut(key) { mutableListOf() }
                while (buffer.size <= chunkIndex) buffer.add(ByteArray(0))
                buffer[chunkIndex] = chunkData
                chunkTotals[key] = totalChunks
                if (buffer.size >= totalChunks && buffer.all { it.isNotEmpty() }) {
                    val assembled = buffer.reduce { acc, bytes -> acc + bytes }
                    _incoming.tryEmit(
                        Transport.TransportFrame(
                            senderEdPubHex = senderPubHex,
                            kind = Transport.TransportFrame.KIND_DATA,
                            payload = assembled
                        )
                    )
                    chunkBuffers.remove(key)
                    chunkTotals.remove(key)
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
