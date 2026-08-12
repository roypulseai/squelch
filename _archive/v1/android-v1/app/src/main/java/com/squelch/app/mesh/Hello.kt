package com.squelch.app.mesh

import com.squelch.app.util.Bytes
import java.io.ByteArrayOutputStream

/**
 * Link-level HELLO identity blob exchanged when a transport link opens
 * (spec 5.3 HELLO). Carries identity + capability flags for transport negotiation.
 */
data class Hello(
    val edPub: ByteArray,
    val xPub: ByteArray,
    val callsign: String,
    val capabilities: Int,
    val address: String = "",
    val deviceName: String = ""
) {
    companion object {
        const val CAP_BLE = 1
        const val CAP_WIFI = 2
        const val CAP_NFC = 4
        const val CAP_RELAY = 8

        fun decode(bytes: ByteArray): Hello {
            require(bytes.size >= 4 && bytes[0] == 'S'.code.toByte() && bytes[1] == 'Q'.code.toByte()) { "bad hello" }
            var p = 4
            var ed = ByteArray(0)
            var x = ByteArray(0)
            var cs = ""
            var addr = ""
            var caps = 0
            var name = ""
            while (p < bytes.size) {
                val tag = bytes[p++]
                if (tag.toInt() == 0) break
                val len = bytes[p++].toInt() and 0xff
                val value = bytes.copyOfRange(p, p + len); p += len
                when (tag) {
                    0x01.toByte() -> ed = value
                    0x02.toByte() -> x = value
                    0x03.toByte() -> cs = String(value, Charsets.UTF_8)
                    0x04.toByte() -> addr = String(value, Charsets.UTF_8)
                    0x05.toByte() -> caps = value[0].toInt() and 0xff
                    0x06.toByte() -> name = String(value, Charsets.UTF_8)
                }
            }
            return Hello(ed, x, cs, caps, addr, name)
        }
    }

    fun encode(): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write('S'.code)
        bos.write('Q'.code)
        bos.write('H'.code)
        bos.write('1'.code)
        fun field(tag: Byte, value: ByteArray) {
            bos.write(tag.toInt())
            bos.write(value.size)
            bos.write(value)
        }
        field(0x01, edPub)
        field(0x02, xPub)
        field(0x03, callsign.toByteArray(Charsets.UTF_8))
        field(0x04, address.toByteArray(Charsets.UTF_8))
        field(0x05, byteArrayOf(capabilities.toByte()))
        if (deviceName.isNotEmpty()) field(0x06, deviceName.toByteArray(Charsets.UTF_8))
        bos.write(0)
        return bos.toByteArray()
    }

    fun supports(cap: Int): Boolean = capabilities and cap != 0
}
