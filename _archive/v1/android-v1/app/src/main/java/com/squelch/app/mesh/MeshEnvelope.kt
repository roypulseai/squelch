package com.squelch.app.mesh

import com.squelch.app.util.Bytes
import java.io.ByteArrayOutputStream

/**
 * Encrypted message envelope carried in a MeshPacket payload (spec 5.1 "Payload"):
 *   kind(1B) | recipLen(1B) | recipient(len) | ciphertext
 *
 * Kinds:
 *   DM   (0x01): recipient = recipient Ed25519 pubkey (32B); Noise E2E transport ciphertext.
 *   ROOM (0x02): recipient = room id (16B); room-key symmetric ciphertext.
 *   HS   (0x03): recipient = target Ed25519 pubkey (32B); Noise handshake bytes.
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

        fun decode(bytes: ByteArray): MeshEnvelope {
            var p = 0
            val kind = bytes[p++]
            val recipLen = bytes[p++].toInt()
            val recipient = bytes.copyOfRange(p, p + recipLen); p += recipLen
            val ciphertext = bytes.copyOfRange(p, bytes.size)
            return MeshEnvelope(kind, recipient, ciphertext)
        }
    }

    fun encode(): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write(kind.toInt())
        bos.write(recipient.size)
        bos.write(recipient)
        bos.write(ciphertext)
        return bos.toByteArray()
    }

    fun addressedTo(edPub: ByteArray): Boolean =
        Bytes.constantTimeEquals(recipient, edPub)
}
