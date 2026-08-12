package com.squelch.app.transport.nfc

import android.nfc.cardemulation.HostApduService
import com.squelch.app.crypto.Ed25519
import com.squelch.app.crypto.Identity
import com.squelch.app.util.Bytes

/**
 * HCE NDEF Type 4 tag emulation (spec 4.3). While the app is in pairing mode,
 * another Squelch phone can tap this one and read its identity:
 *   edPub(32) || xPub(32) || nonce(16) || sig(64)
 * where sig = Ed25519-sign(edPub || xPub || nonce).
 *
 * The nonce is served fresh on each read and is returned over the BLE KK
 * handshake afterwards, binding the tap to the link (proximity proof).
 */
class HceNdefService : HostApduService() {

    companion object {
        val AID_SQUELCH: ByteArray = Bytes.unhex("F27625737132")
        val AID_NDEF: ByteArray = Bytes.unhex("D2760000850101")

        private val CC_FILE = byteArrayOf(
            0x00.toByte(), 0x0F.toByte(), // CCLEN = 15
            0x20.toByte(),                // mapping version 2.0
            0x00.toByte(),                // MLe (default)
            0xFF.toByte(),                // MLc
            0x00.toByte(),                // T
            0xFF.toByte(),                // L
            0x04.toByte(),                // NDEF File Control TLV tag
            0x06.toByte(),                // ... length
            (0xE1).toByte(), (0x04).toByte(), // file id E104
            0x02.toByte(), 0x00.toByte(),    // max NDEF size 512
            0x00.toByte(),                // read access
            0xFF.toByte()                 // write access (read-only to peers)
        )

        const val FILE_CC = 0xE103
        const val FILE_NDEF = 0xE104
        private const val SW_SUCCESS = 0x9000
        private const val SW_FILE_NOT_FOUND = 0x6A82
        private const val SW_NOT_SUPPORTED = 0x6A00

        @Volatile
        var identity: Identity? = null

        @Volatile
        var lastNonce: ByteArray? = null

        fun buildIdentityPayload(): ByteArray {
            val id = identity ?: return ByteArray(0)
            val nonce = Bytes.randomId(java.security.SecureRandom(), 16)
            lastNonce = nonce
            val signed = Bytes.concat(id.edPub, id.xPub, nonce)
            val sig = Ed25519.sign(id.edSeed, signed)
            return Bytes.concat(id.edPub, id.xPub, nonce, sig)
        }
    }

    private var currentFile = 0
    private var aidSelected = false

    override fun processCommandApdu(commandApdu: ByteArray, extras: android.os.Bundle): ByteArray {
        if (commandApdu.isEmpty()) return sw(SW_NOT_SUPPORTED)
        val cla = commandApdu[0].toInt() and 0xff
        val ins = commandApdu[1].toInt() and 0xff
        val p1 = commandApdu[2].toInt() and 0xff
        val p2 = commandApdu[3].toInt() and 0xff

        return when (ins) {
            0xA4 -> handleSelect(commandApdu, p1, p2)
            0xB0 -> handleReadBinary(commandApdu)
            0xD6, 0x00 -> sw(SW_FILE_NOT_FOUND) // writes unsupported
            else -> sw(SW_NOT_SUPPORTED)
        }
    }

    private fun handleSelect(cmd: ByteArray, p1: Int, p2: Int): ByteArray {
        if (p1 == 0x04) { // SELECT by AID
            val data = if (cmd.size > 5) cmd.copyOfRange(5, cmd.size) else ByteArray(0)
            if (Bytes.constantTimeEquals(data, AID_SQUELCH) || Bytes.constantTimeEquals(data, AID_NDEF)) {
                aidSelected = true
                currentFile = 0
                return sw(SW_SUCCESS)
            }
            return sw(0x6A82)
        }
        if (p1 == 0x00 && (p2 == 0x0C || p2 == 0x04)) { // SELECT by DF name
            if (cmd.size < 7) return sw(0x6A82)
            val fileId = Bytes.intFrom16(cmd[5], cmd[6])
            return if (fileId == FILE_CC || fileId == FILE_NDEF) {
                currentFile = fileId
                sw(SW_SUCCESS)
            } else {
                sw(0x6A82)
            }
        }
        if (p1 == 0x00 && p2 == 0x00) { // SELECT first or by DF name on some readers
            currentFile = 0
            return sw(SW_SUCCESS)
        }
        return sw(0x6A82)
    }

    private fun handleReadBinary(cmd: ByteArray): ByteArray {
        if (cmd.size < 5) return sw(0x6700)
        val offset = Bytes.intFrom16(cmd[2], cmd[3])
        val length = (cmd[4].toInt() and 0xff).let { if (it == 0) 256 else it }
        val file = when (currentFile) {
            FILE_CC -> CC_FILE
            FILE_NDEF -> buildNdefFile()
            else -> return sw(SW_FILE_NOT_FOUND)
        }
        if (offset >= file.size) return sw(0x6A82)
        val end = (offset + length).coerceAtMost(file.size)
        val data = file.copyOfRange(offset, end)
        val out = ByteArray(data.size + 2)
        System.arraycopy(data, 0, out, 0, data.size)
        out[out.size - 2] = 0x90.toByte()
        out[out.size - 1] = 0x00.toByte()
        return out
    }

    /** NDEF message: "application/x-squelch-ident" MIME record carrying the identity blob. */
    private fun buildNdefFile(): ByteArray {
        val payload = buildIdentityPayload()
        if (payload.isEmpty()) return ByteArray(0)
        val type = "application/x-squelch-ident".toByteArray(Charsets.US_ASCII)
        // NDEF record (short, no id, MB|ME|SR, TNF=MIME 0x01)
        val record = ByteArray(2 + type.size + payload.size)
        record[0] = (0x90 or 0x01).toByte() // MB|ME|SR | TNF 0x01
        record[1] = type.size.toByte()
        record[2] = payload.size.toByte()
        System.arraycopy(type, 0, record, 3, type.size)
        System.arraycopy(payload, 0, record, 3 + type.size, payload.size)

        // NDEF file TLV: 03 len(len2) msg FE
        val file = ByteArray(4 + record.size)
        file[0] = 0x03
        file[1] = ((record.size ushr 8) and 0xff).toByte()
        file[2] = (record.size and 0xff).toByte()
        System.arraycopy(record, 0, file, 3, record.size)
        file[file.size - 1] = 0xFE.toByte()
        return file
    }

    private fun sw(statusWord: Int): ByteArray = byteArrayOf(
        ((statusWord ushr 8) and 0xff).toByte(), (statusWord and 0xff).toByte()
    )

    override fun onDeactivated(reason: Int) {
        currentFile = 0
        aidSelected = false
    }
}
