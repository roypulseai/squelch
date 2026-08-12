package com.squelch.app.transport.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.squelch.app.mesh.LinkCodec
import com.squelch.app.transport.MeshLink
import com.squelch.app.transport.MeshLinkListener
import com.squelch.app.transport.MeshTransport
import com.squelch.app.util.Bytes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * BLE mesh backbone (spec 4.1): always-on advertising + scanning.
 *
 * One device is the GATT server (advertiser); peers connect as GATT clients.
 * A stable tie-break (first 8 bytes of Ed25519 pubkey, lexicographic) decides
 * who connects: the larger key connects as client, the smaller waits as server,
 * so exactly one connection forms per peer pair. Re-broadcasting is handled by
 * the routing layer; this transport only carries point-to-point link frames.
 */
class BleTransport(
    private val context: Context,
    private val listener: MeshLinkListener,
    private val tieBreakId: ByteArray
) : MeshTransport {

    override val name = "BLE"

    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter get() = manager?.adapter
    override val supported: Boolean get() = adapter != null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val links = ConcurrentHashMap<String, BleLink>()

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var advertising = false
    private var scanning = false

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("4f4a7a21-1c3e-4b2f-9c81-7e5b4a0d9f32")
        private val RX_UUID: UUID = UUID.fromString("4f4a7a22-1c3e-4b2f-9c81-7e5b4a0d9f32") // client -> server
        private val TX_UUID: UUID = UUID.fromString("4f4a7a23-1c3e-4b2f-9c81-7e5b4a0d9f32") // server -> client
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MANUFACTURER_ID = 0xFFFF
        private const val DEFAULT_MTU = 23
    }

    private val bluetoothAddress: String get() = adapter?.address ?: ""

    override fun start() {
        executor.execute {
            startServer()
            startAdvertising()
            startScanning()
        }
    }

    override fun stop() {
        executor.execute {
            stopScanning()
            stopAdvertising()
            closeServer()
        }
    }

    // ---- GATT server ----

    private fun startServer() {
        val a = adapter ?: return
        val server = openGattServer(a) ?: return
        gattServer = server
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val rx = BluetoothGattCharacteristic(
            RX_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val tx = BluetoothGattCharacteristic(
            TX_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(rx)
        service.addCharacteristic(tx)
        server.addService(service)
    }

    private fun closeServer() {
        gattServer?.close()
        gattServer = null
    }

    /** openGattServer is absent from the compile-time SDK stub; reflect it (present on real devices). */
    private fun openGattServer(a: BluetoothAdapter): BluetoothGattServer? {
        return try {
            val m = a.javaClass.getMethod(
                "openGattServer",
                Context::class.java,
                BluetoothGattServerCallback::class.java
            )
            m.invoke(a, context, serverCallback) as? BluetoothGattServer
        } catch (e: Exception) {
            null
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val link = links.getOrPut(device.address) {
                    BleLink(device, isServerRole = true, transportName = "BLE", tieBreakId = tieBreakId, mtu = DEFAULT_MTU).also { l ->
                        l.listener = this@BleTransport::onLinkFrame
                        l.closedHandler = { onLinkClosed(l) }
                        listener.onLinkOpen(l)
                    }
                }
                link.adapter = adapter
                link.server = gattServer
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                links.remove(device.address)?.close()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            links[device.address]?.feedChunk(value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            links[device.address]?.mtu = mtu
        }
    }

    // ---- advertising ----

    private fun startAdvertising() {
        val a = adapter ?: return
        val adv = a.bluetoothLeAdvertiser ?: return
        advertiser = adv
        val settings = android.bluetooth.le.AdvertiseSettings.Builder()
            .setAdvertiseMode(android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = android.bluetooth.le.AdvertiseData.Builder()
            .addServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .setIncludeTxPowerLevel(true)
            .addManufacturerData(MANUFACTURER_ID, tieBreakId.copyOf(8))
            .build()
        try {
            adv.startAdvertising(settings, data, advertiserCallback)
            advertising = true
        } catch (e: Exception) {
            // Advertising already active or adapter busy; reconnect logic in service.
        }
    }

    private fun stopAdvertising() {
        if (advertising) {
            advertiser?.stopAdvertising(advertiserCallback)
            advertising = false
        }
    }

    private val advertiserCallback = object : android.bluetooth.le.AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: android.bluetooth.le.AdvertiseSettings) {
            advertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
        }
    }

    // ---- scanning ----

    private fun startScanning() {
        val a = adapter ?: return
        val sc = a.bluetoothLeScanner ?: return
        scanner = sc
        val filters = listOf(
            android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
                .build()
        )
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            sc.startScan(filters, settings, scanCallback)
            scanning = true
        } catch (e: Exception) {
            scanning = false
        }
    }

    private fun stopScanning() {
        if (scanning) {
            scanner?.stopScan(scanCallback)
            scanning = false
        }
    }

    private val scanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val device = result.device ?: return
            if (links.containsKey(device.address)) return
            val record = result.scanRecord ?: return
            val mfg = record.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
            if (mfg.size < 8) return

            // Tie-break: the device with the LARGER key hash connects as GATT client.
            if (Bytes.compareUnsigned(tieBreakId, mfg.copyOf(8)) <= 0) return
            connectTo(device)
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        val a = adapter ?: return
        val gatt = device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
        val link = BleLink(device, isServerRole = false, transportName = "BLE", tieBreakId = tieBreakId, mtu = DEFAULT_MTU)
        link.gatt = gatt
        link.listener = this::onLinkFrame
        link.closedHandler = { onLinkClosed(link) }
        link.adapter = adapter
        links[device.address] = link
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val link = links[gatt.device.address] ?: return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                links.remove(gatt.device.address)?.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val link = links[gatt.device.address] ?: return
            val service = gatt.getService(SERVICE_UUID) ?: return
            val tx = service.getCharacteristic(TX_UUID) ?: return
            gatt.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            }
            gatt.requestMtu(512)
            listener.onLinkOpen(link)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            links[gatt.device.address]?.feedChunk(value)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            links[gatt.device.address]?.mtu = mtu
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            links[gatt.device.address]?.onWriteDone(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    // ---- plumbing to engine ----

    private fun onLinkFrame(link: MeshLink, kind: Byte, message: ByteArray) {
        listener.onFrame(link, kind, message)
    }

    private fun onLinkClosed(link: MeshLink) {
        listener.onLinkClosed(link)
    }

    /** Per-connection link over BLE (works for both server and client roles). */
    private class BleLink(
        private val device: BluetoothDevice,
        val isServerRole: Boolean,
        override val transportName: String,
        private val tieBreakId: ByteArray,
        var mtu: Int
    ) : MeshLink {
        var gatt: BluetoothGatt? = null
        var adapter: android.bluetooth.BluetoothAdapter? = null
        var server: BluetoothGattServer? = null
        var listener: ((MeshLink, Byte, ByteArray) -> Unit)? = null
        var closedHandler: (() -> Unit)? = null

        private val assembler = LinkCodec.Assembler { kind, message ->
            listener?.invoke(this, kind, message)
        }
        private val pending = ArrayDeque<ByteArray>()
        private var writing = false
        private var closed = false

        private val chunkCap get() = (mtu - 3).coerceIn(20, 509)

        override val isNoiseInitiator: Boolean get() = !isServerRole

        override fun sendFrame(kind: Byte, message: ByteArray) {
            val chunks = LinkCodec.encodeChunks(kind, message, chunkCap)
            for (c in chunks) {
                if (isServerRole) {
                    sendServer(c)
                } else {
                    enqueueClient(c)
                }
            }
        }

        private fun sendServer(chunk: ByteArray) {
            val srv = server ?: return
            val tx = srv.getService(SERVICE_UUID)?.getCharacteristic(TX_UUID) ?: return
            try {
                srv.notifyCharacteristicChanged(device, tx, false, chunk)
            } catch (e: Exception) {
            }
        }

        private fun enqueueClient(chunk: ByteArray) {
            synchronized(this) {
                pending.addLast(chunk)
            }
            pump()
        }

        private fun pump() {
            val g = gatt ?: return
            val chunk = synchronized(this) {
                if (writing || pending.isEmpty()) return
                writing = true
                pending.removeFirst()
            }
            val rx = g.getService(SERVICE_UUID)?.getCharacteristic(RX_UUID) ?: run {
                synchronized(this) { writing = false }
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(rx, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    rx.value = chunk
                    g.writeCharacteristic(rx)
                }
            } catch (e: Exception) {
                synchronized(this) { writing = false }
            }
        }

        fun onWriteDone(success: Boolean) {
            synchronized(this) { writing = false }
            pump()
        }

        fun feedChunk(chunk: ByteArray) {
            assembler.onChunk(chunk)
        }

        override fun close() {
            if (closed) return
            closed = true
            closedHandler?.invoke()
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: Exception) {
            }
            gatt = null
        }
    }
}
