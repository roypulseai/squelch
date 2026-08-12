package com.squelch.app.mesh

import com.squelch.app.crypto.Ed25519
import com.squelch.app.util.Bytes

/**
 * Wire packet (spec 5.1) for content flowing over a NearBy link:
 *
 *   Version(1B) | MsgID(16B) | TTL(1B) | SenderPK(32B) | Sig(64B) | Payload
 *
 * Signature covers (MsgID || Payload) so forwarding hops can't tamper.
 */
data class MeshPacket(
    val msgId: ByteArray,
    val ttl: Int,
    val senderPk: ByteArray,
    val sig: ByteArray,
    val payload: ByteArray
) {
    companion object {
        const val VERSION: Byte = 0x01
        const val HEADER_SIZE = 1 + 16 + 1 + 32 + 64

        fun encode(p: MeshPacket): ByteArray {
            val out = ByteArray(HEADER_SIZE + p.payload.size)
            var p0 = 0
            out[p0++] = VERSION
            System.arraycopy(p.msgId, 0, out, p0, 16); p0 += 16
            out[p0++] = (p.ttl and 0xff).toByte()
            System.arraycopy(p.senderPk, 0, out, p0, 32); p0 += 32
            System.arraycopy(p.sig, 0, out, p0, 64); p0 += 64
            System.arraycopy(p.payload, 0, out, p0, p.payload.size)
            return out
        }

        fun decode(bytes: ByteArray): MeshPacket {
            require(bytes.size > HEADER_SIZE) { "packet too short" }
            var p = 0
            require(bytes[p++] == VERSION) { "unsupported packet version" }
            val msgId = bytes.copyOfRange(p, p + 16); p += 16
            val ttl = bytes[p++].toInt() and 0xff
            val senderPk = bytes.copyOfRange(p, p + 32); p += 32
            val sig = bytes.copyOfRange(p, p + 64); p += 64
            val payload = bytes.copyOfRange(p, bytes.size)
            return MeshPacket(msgId, ttl, senderPk, sig, payload)
        }

        /** Sign + assemble. */
        fun sign(
            msgId: ByteArray,
            ttl: Int,
            senderEdSeed: ByteArray,
            senderEdPub: ByteArray,
            payload: ByteArray
        ): MeshPacket {
            val signed = Bytes.concat(msgId, payload)
            val sig = Ed25519.sign(senderEdSeed, signed)
            return MeshPacket(msgId, ttl, senderEdPub, sig, payload)
        }
    }

    fun encode(): ByteArray = encode(this)

    fun verifySignature(): Boolean {
        val signed = Bytes.concat(msgId, payload)
        return Ed25519.verify(senderPk, signed, sig)
    }

    fun withTtl(newTtl: Int): MeshPacket = copy(ttl = newTtl)
}
