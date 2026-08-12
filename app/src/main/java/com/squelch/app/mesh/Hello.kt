package com.squelch.app.mesh

import com.squelch.app.util.Bytes

/**
 * Edge-identify beacon (spec 5.3). After the first frame a peer
 * transmits after connecting, every endpoint knows the others'
 * long-term Noise static keys (xPub) + call-sign + capabilities.
 *
 *   "SQH1" (4B) | flags(1B) | edPub(32B) | xPub(32B) | callsignLen(1B) |
 *   callsign(callLen) | caps(1B) | deviceNameLen(1B) | deviceName(nameLen)
 *
 * For v0.7 the callsign is the first 8 hex chars of sha256(edPub || xPub)
 * - it matches the user-facing "call-sign" message header the spec
 * describes.
 */
data class Hello(
    val edPub: ByteArray,
    val xPub: ByteArray,
    val callsign: String,
    val capabilities: Int,
    val deviceName: String = ""
) {
    companion object {
        const val CAP_PLAIN = 1
        const val CAP_AES_GCM = 2

        private const val MAGIC = "SQH1"

        fun encode(h: Hello): ByteArray {
            val callsignBytes = h.callsign.toByteArray(Charsets.UTF_8)
            val nameBytes = h.deviceName.toByteArray(Charsets.UTF_8)
            val out = ByteArray(4 + 1 + 32 + 32 + 1 + callsignBytes.size + 1 + 1 + nameBytes.size)
            var p = 0
            for (c in MAGIC) out[p++] = c.code.toByte()
            out[p++] = 0  // flags
            System.arraycopy(h.edPub, 0, out, p, 32); p += 32
            System.arraycopy(h.xPub, 0, out, p, 32); p += 32
            out[p++] = callsignBytes.size.toByte()
            System.arraycopy(callsignBytes, 0, out, p, callsignBytes.size); p += callsignBytes.size
            out[p++] = h.capabilities.toByte()
            out[p++] = nameBytes.size.toByte()
            System.arraycopy(nameBytes, 0, out, p, nameBytes.size)
            return out
        }

        fun decode(bytes: ByteArray): Hello {
            require(bytes.size >= 4) { "hello too short" }
            require(
                bytes[0] == 'S'.code.toByte() &&
                bytes[1] == 'Q'.code.toByte() &&
                bytes[2] == 'H'.code.toByte() &&
                bytes[3] == '1'.code.toByte()
            ) { "bad hello magic" }
            var p = 4
            p++ // flags
            val edPub = bytes.copyOfRange(p, p + 32); p += 32
            val xPub = bytes.copyOfRange(p, p + 32); p += 32
            val callsignLen = bytes[p++].toInt() and 0xff
            val callsign = String(bytes.copyOfRange(p, p + callsignLen), Charsets.UTF_8); p += callsignLen
            val caps = bytes[p++].toInt() and 0xff
            val nameLen = bytes[p++].toInt() and 0xff
            val name = String(bytes.copyOfRange(p, p + nameLen), Charsets.UTF_8); p += nameLen
            return Hello(edPub, xPub, callsign, caps, name)
        }

        fun callsignFor(edPub: ByteArray, xPub: ByteArray): String {
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Bytes.concat(edPub, xPub))
            return Bytes.hex(hash.copyOf(4))
        }
    }

    fun encode(): ByteArray = encode(this)
}
