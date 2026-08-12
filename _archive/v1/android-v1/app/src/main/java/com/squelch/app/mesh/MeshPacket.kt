package com.squelch.app.mesh

import com.squelch.app.crypto.Ed25519
import com.squelch.app.util.Bytes
import java.io.ByteArrayOutputStream

/**
 * Wire packet (spec 5.1):
 *   Version(1B) | MsgID(16B) | TTL(1B) | SenderPK(32B) | Sig(64B) | Payload
 * Sig covers (MsgID || Payload) so forwarding hops cannot tamper.
 */
data class MeshPacket(
    val msgId: ByteArray,
    var ttl: Int,
    val senderPk: ByteArray,
    val sig: ByteArray,
    val payload: ByteArray
) {
    companion object {
        const val VERSION: Byte = 0x01
        const val HEADER_SIZE = 1 + 16 + 1 + 32 + 64
        const val DEFAULT_TTL = 6

        fun decode(bytes: ByteArray): MeshPacket {
            require(bytes.size > HEADER_SIZE) { "packet too short" }
            var p = 0
            require(bytes[p++] == VERSION) { "unsupported packet version" }
            val msgId = bytes.copyOfRange(p, p + 16); p += 16
            val ttl = bytes[p++].toInt()
            val senderPk = bytes.copyOfRange(p, p + 32); p += 32
            val sig = bytes.copyOfRange(p, p + 64); p += 64
            val payload = bytes.copyOfRange(p, bytes.size)
            return MeshPacket(msgId, ttl, senderPk, sig, payload)
        }
    }

    fun encode(): ByteArray {
        val bos = ByteArrayOutputStream(HEADER_SIZE + payload.size)
        bos.write(VERSION.toInt())
        bos.write(msgId)
        bos.write(ttl and 0xff)
        bos.write(senderPk)
        bos.write(sig)
        bos.write(payload)
        return bos.toByteArray()
    }

    /** Verify the Ed25519 signature over MsgID || Payload. */
    fun verifySignature(): Boolean {
        val signed = Bytes.concat(msgId, payload)
        return Ed25519.verify(senderPk, signed, sig)
    }

    fun withTtl(newTtl: Int): MeshPacket = copy(ttl = newTtl)
}
