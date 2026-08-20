package com.squelch.app.mesh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class BleTransport(private val context: Context) : Transport {

    companion object {
        private const val TAG = "BleTransport"
        val SERVICE_UUID: UUID = UUID.fromString("6b5b17a0-e4f8-4e4e-a0b4-f2c5d1e8f900")
    }

    override val name: String = "BLE"

    private val _incoming = MutableSharedFlow<Transport.TransportFrame>(extraBufferCapacity = 16)
    override val incoming: SharedFlow<Transport.TransportFrame> = _incoming.asSharedFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    @Volatile private var running = false
    private var scope: CoroutineScope? = null

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
        Log.d(TAG, "BLE transport started")
    }

    override fun stop() {
        running = false
        stopAdvertising()
        stopScanning()
        scope?.cancel()
        scope = null
        Log.d(TAG, "BLE transport stopped")
    }

    override fun send(recipientEdPubHex: String, payload: ByteArray) {
        if (!running) return
        Log.d(TAG, "BLE send: ${payload.size} bytes to $recipientEdPubHex (broadcast)")
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val adapter = bluetoothAdapter ?: return
        advertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Advertising started")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed: $errorCode")
            }
        }
        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
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

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        scanner = adapter.bluetoothLeScanner ?: return

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val data = result.scanRecord?.serviceData?.get(ParcelUuid(SERVICE_UUID))
                if (data != null && data.isNotEmpty()) {
                    scope?.launch {
                        if (data.size > 34) {
                            val edPubHex = com.squelch.app.util.Bytes.hex(data.copyOfRange(2, 34))
                            _incoming.emit(
                                Transport.TransportFrame(
                                    senderEdPubHex = edPubHex,
                                    kind = Transport.TransportFrame.KIND_HELLO,
                                    payload = data
                                )
                            )
                        }
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

        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
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
}
