package com.squelch.app.transport.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import com.squelch.app.crypto.Ed25519
import com.squelch.app.util.Bytes

/**
 * NFC pairing reader (spec 4.3). Enables reader mode; when a device is tapped,
 * reads the Type 4 NDEF identity served by HceNdefService via raw ISO-DEP APDUs,
 * verifies the Ed25519 signature, and reports the result.
 */
class NfcTapManager(private val activity: Activity) {

    interface Callback {
        fun onIdentityRead(edPub: ByteArray, xPub: ByteArray, nonce: ByteArray)
        fun onError(message: String)
    }

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun enable(callback: Callback) {
        val nfc = adapter ?: return
        nfc.enableReaderMode(
            activity,
            NfcAdapter.ReaderCallback { tag -> read(tag, callback) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            Bundle()
        )
    }

    fun disable() {
        adapter?.disableReaderMode(activity)
    }

    private fun read(tag: Tag, callback: Callback) {
        try {
            val iso = IsoDep.get(tag) ?: run {
                callback.onError("Not an ISO-DEP tag (is the partner app in tap mode?)")
                return
            }
            iso.connect()
            try {
                iso.timeout = 2000
                selectAid(iso, HceNdefService.AID_NDEF)
                selectFile(iso, HceNdefService.FILE_CC)
                readFile(iso, HceNdefService.FILE_NDEF)?.let { ndefFile ->
                    val payload = extractPayload(ndefFile)
                    if (payload != null && payload.size == 144) {
                        verify(payload, callback)
                    } else {
                        callback.onError("Tapped tag has no valid Squelch identity")
                    }
                } ?: callback.onError("Could not read NDEF file")
            } finally {
                try {
                    iso.close()
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
            callback.onError(e.message ?: "NFC read failed")
        }
    }

    private fun transceive(iso: IsoDep, apdu: ByteArray, expectedSw: Int = 0x9000): ByteArray? {
        val resp = iso.transceive(apdu)
        if (resp.size < 2) return null
        val sw = Bytes.intFrom16(resp[resp.size - 2], resp[resp.size - 1])
        if (sw != expectedSw) return null
        return resp.copyOfRange(0, resp.size - 2)
    }

    private fun selectAid(iso: IsoDep, aid: ByteArray) {
        val apdu = ByteArray(6 + aid.size)
        apdu[0] = 0x00
        apdu[1] = 0xA4.toByte()
        apdu[2] = 0x04
        apdu[3] = 0x00
        apdu[4] = aid.size.toByte()
        System.arraycopy(aid, 0, apdu, 5, aid.size)
        apdu[5 + aid.size] = 0x00
        transceive(iso, apdu)
    }

    private fun selectFile(iso: IsoDep, fileId: Int) {
        val apdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02,
            ((fileId ushr 8) and 0xff).toByte(), (fileId and 0xff).toByte()
        )
        transceive(iso, apdu)
    }

    private fun readFile(iso: IsoDep, fileId: Int): ByteArray? {
        selectFile(iso, fileId)
        val out = java.io.ByteArrayOutputStream()
        var offset = 0
        while (true) {
            val apdu = byteArrayOf(0x00, 0xB0.toByte(), ((offset ushr 8) and 0xff).toByte(), (offset and 0xff).toByte(), 0x00)
            val chunk = transceive(iso, apdu) ?: break
            if (chunk.isEmpty()) break
            out.write(chunk)
            offset += chunk.size
            if (chunk.size < 255) break
        }
        val file = out.toByteArray()
        return if (file.isEmpty()) null else file
    }

    /** Parse the Type 4 NDEF file TLV (03 <len> <msg> FE) and pull our record's payload. */
    private fun extractPayload(file: ByteArray): ByteArray? {
        // File: 0x03 LEN_LO/HI NDEF_MSG 0xFE (or LEN as 2 bytes)
        if (file.size < 5 || file[0].toInt() != 0x03) return null
        var p = 1
        val lenHi = file[p++].toInt() and 0xff
        val lenLo = file[p++].toInt() and 0xff
        var msgLen = (lenHi shl 8) or lenLo
        if (msgLen == 0 && lenHi == 0) msgLen = 0xFF // some cards use 1-byte length
        if (p + msgLen > file.size) msgLen = file.size - p
        val msg = file.copyOfRange(p, p + msgLen)

        // NDEF record: header(1B) typeLen(1B) payloadLen(1B) type payload (short record)
        if (msg.size < 3) return null
        val header = msg[0].toInt() and 0xff
        if ((header and 0x10) == 0) return null // not SR
        val typeLen = msg[1].toInt() and 0xff
        val payloadLen = msg[2].toInt() and 0xff
        val start = 3 + typeLen
        if (start + payloadLen > msg.size) return null
        return msg.copyOfRange(start, start + payloadLen)
    }

    private fun verify(payload: ByteArray, callback: Callback) {
        val edPub = payload.copyOfRange(0, 32)
        val xPub = payload.copyOfRange(32, 64)
        val nonce = payload.copyOfRange(64, 80)
        val sig = payload.copyOfRange(80, 144)
        val signed = Bytes.concat(edPub, xPub, nonce)
        if (Ed25519.verify(edPub, signed, sig)) {
            callback.onIdentityRead(edPub, xPub, nonce)
        } else {
            callback.onError("Identity signature verification failed")
        }
    }
}
