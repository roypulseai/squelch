package com.squelch.app.mesh

import com.squelch.app.util.Bytes

/**
 * Encrypted envelope carried in the [MeshPacket.payload] field (spec 5.1):
 *
 *   kind(1B) | recipLen(1B) | recipient(len) | ciphertext
 *
 *   DM   (0x01):  recipient = recipient Ed25519 pubkey (32B)
 *                  payload   = Noise transport ciphertext
 *   ROOM (0x02):  recipient = room id (16B)
 *                  payload   = AES-GCM ciphertext
 *   HS   (0x03):  recipient = target Ed25519 pubkey (32B)
 *                  payload   = raw Noise handshake bytes
 */
data class MeshEnvelope(
    val kind: Byte,
    val recipient: ByteArray,
    val ciphertext: ByteArray
) {
    companion object {
        const val KIND_DM: Byte = 0x01
        const val KIND_ROOM: Byte = 0x02
        const val KIND_HS: Byte = 0x03

        fun encode(e: MeshEnvelope): ByteArray {
            val out = ByteArray(2 + e.recipient.size + e.ciphertext.size)
            var p = 0
            out[p++] = e.kind
            out[p++] = e.recipient.size.toByte()
            System.arraycopy(e.recipient, 0, out, p, e.recipient.size); p += e.recipient.size
            System.arraycopy(e.ciphertext, 0, out, p, e.ciphertext.size)
            return out
        }

        fun decode(bytes: ByteArray): MeshEnvelope {
            require(bytes.size >= 2)
            val kind = bytes[0]
            val rLen = bytes[1].toInt() and 0xff
            require(2 + rLen <= bytes.size)
            val recipient = bytes.copyOfRange(2, 2 + rLen)
            val ciphertext = bytes.copyOfRange(2 + rLen, bytes.size)
            return MeshEnvelope(kind, recipient, ciphertext)
        }
    }

    fun encode(): ByteArray = encode(this)

    fun addressedTo(pubkey: ByteArray): Boolean =
        Bytes.constantTimeEquals(recipient, pubkey)
}
