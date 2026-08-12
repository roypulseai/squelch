package com.squelch.app.mesh

import com.squelch.app.util.Bytes

/**
 * Link-level framing used by every physical transport. One frame kind byte
 * + the inner bytes; chunking for BLE MTUs is enforced per-frame by the
 * transport layer (AndroidMeshManager already enforces the 32 KiB cap).
 *
 *   Chunked: [kind(1B)] [len(2B)] [data] | [kind(1B)] [len(2B)] [data] ...
 *
 * Frame kinds are constants from [AndroidMeshManager]:
 *   KIND_HELLO = 0x01  - edge-detected identity beacon
 *   KIND_DATA   = 0x02  - framed MeshPacket (signed + TTL/etc.)
 */
object LinkCodec {
    /**
     * Build a single frame carrying `payload` of `kind`. Multi-frame
     * packing is the responsibility of the transport layer.
     */
    fun frame(kind: Byte, payload: ByteArray): ByteArray {
        val out = ByteArray(3 + payload.size)
        out[0] = kind
        out[1] = ((payload.size ushr 8) and 0xff).toByte()
        out[2] = (payload.size and 0xff).toByte()
        System.arraycopy(payload, 0, out, 3, payload.size)
        return out
    }

    /**
     * Parse a single frame. Returns null when [bytes] is too short.
     * For multi-frame assembly the transport keeps its own buffer.
     */
    data class Frame(val kind: Byte, val payload: ByteArray)

    fun tryParse(bytes: ByteArray): Frame? {
        if (bytes.size < 3) return null
        val len = Bytes.intFrom16(bytes[1], bytes[2])
        if (bytes.size < 3 + len) return null
        return Frame(bytes[0], bytes.copyOfRange(3, 3 + len))
    }
}
