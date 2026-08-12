package com.squelch.app.mesh

import com.squelch.app.util.Bytes

/**
 * Inner plaintext message carried inside an envelope (chat / ack / room):
 *   kind(1B) | ts(8B) | msgId(16B) | bodyLen(2B) | body
 *
 * Mirror of the v1 InnerMessage. Used both as in-memory state and as the
 * wire format for non-secure payload inspection (the encrypted envelope
 * wraps this byte-string).
 */
data class InnerMessage(
    val kind: Byte,
    val timestamp: Long,
    val msgId: ByteArray,
    val body: ByteArray
) {
    companion object {
        const val KIND_CHAT: Byte = 0x01
        const val KIND_ROOM_MSG: Byte = 0x02
        const val KIND_ACK: Byte = 0x03
        const val KIND_GAME_MOVE: Byte = 0x04
        const val KIND_ROOM_JOIN: Byte = 0x05
        const val KIND_ROOM_LEAVE: Byte = 0x06

        fun chat(timestamp: Long, msgId: ByteArray, text: String): InnerMessage =
            InnerMessage(KIND_CHAT, timestamp, msgId, text.toByteArray(Charsets.UTF_8))

        fun roomMsg(timestamp: Long, msgId: ByteArray, text: String): InnerMessage =
            InnerMessage(KIND_ROOM_MSG, timestamp, msgId, text.toByteArray(Charsets.UTF_8))

        fun ack(msgId: ByteArray): InnerMessage =
            InnerMessage(KIND_ACK, System.currentTimeMillis(), Bytes.randomId(java.security.SecureRandom()), msgId)

        fun joinRoom(timestamp: Long, msgId: ByteArray, roomName: String): InnerMessage =
            InnerMessage(KIND_ROOM_JOIN, timestamp, msgId, roomName.toByteArray(Charsets.UTF_8))

        fun leaveRoom(timestamp: Long, msgId: ByteArray, roomName: String): InnerMessage =
            InnerMessage(KIND_ROOM_LEAVE, timestamp, msgId, roomName.toByteArray(Charsets.UTF_8))

        fun decode(bytes: ByteArray): InnerMessage {
            var p = 0
            val kind = bytes[p++]
            val ts = readLong(bytes, p); p += 8
            val msgId = bytes.copyOfRange(p, p + 16); p += 16
            val bodyLen = Bytes.intFrom16(bytes[p], bytes[p + 1]); p += 2
            val body = bytes.copyOfRange(p, p + bodyLen)
            return InnerMessage(kind, ts, msgId, body)
        }
    }

    val text: String get() = String(body, Charsets.UTF_8)

    fun encode(): ByteArray {
        val out = ByteArray(12 + 16 + 2 + body.size)
        var p = 0
        out[p++] = kind
        for (i in 0 until 8) out[p++] = ((timestamp ushr (56 - i * 8)) and 0xff).toByte()
        System.arraycopy(msgId, 0, out, p, 16); p += 16
        out[p++] = ((body.size ushr 8) and 0xff).toByte()
        out[p++] = (body.size and 0xff).toByte()
        System.arraycopy(body, 0, out, p, body.size)
        return out
    }
}

private fun readLong(bytes: ByteArray, offset: Int): Long {
    var v = 0L
    for (i in 0 until 8) v = (v shl 8) or (bytes[offset + i].toLong() and 0xff)
    return v
}
