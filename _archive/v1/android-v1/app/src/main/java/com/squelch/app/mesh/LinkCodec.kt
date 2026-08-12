package com.squelch.app.mesh

import com.squelch.app.util.Bytes

/**
 * Link-level framing used by every physical transport (BLE GATT, WiFi sockets).
 * Messages are chunked so they fit small MTUs (BLE) without transport-specific code.
 *
 * Chunk: [flags(1B)][seq(1B)][len(2B)][data]
 *   flags: 0x80 = first, 0x40 = last
 * Frame:  kind(1B) || message. Kinds: HELLO, MESH.
 */
object LinkCodec {
    const val KIND_HELLO: Byte = 0x01
    const val KIND_MESH: Byte = 0x02

    private const val FLAG_FIRST: Int = 0x80
    private const val FLAG_LAST: Int = 0x40
    const val CHUNK_HEADER = 4
    const val MAX_FRAME = 64 * 1024

    fun encodeChunks(kind: Byte, message: ByteArray, maxChunkSize: Int): List<ByteArray> {
        require(message.size <= MAX_FRAME) { "frame too large: ${message.size}" }
        val frame = Bytes.concat(byteArrayOf(kind), message)
        val cap = (maxChunkSize - CHUNK_HEADER).coerceAtLeast(16)
        val count = (frame.size + cap - 1) / cap
        val chunks = ArrayList<ByteArray>(count)
        var offset = 0
        for (seq in 0 until count) {
            val dataLen = minOf(cap, frame.size - offset)
            val data = frame.copyOfRange(offset, offset + dataLen)
            offset += dataLen
            val flags = (if (seq == 0) FLAG_FIRST else 0) or (if (seq == count - 1) FLAG_LAST else 0)
            val chunk = ByteArray(CHUNK_HEADER + dataLen)
            chunk[0] = flags.toByte()
            chunk[1] = seq.toByte()
            chunk[2] = ((dataLen ushr 8) and 0xff).toByte()
            chunk[3] = (dataLen and 0xff).toByte()
            System.arraycopy(data, 0, chunk, CHUNK_HEADER, dataLen)
            chunks.add(chunk)
        }
        return chunks
    }

    /** Reassembles chunks into (kind, message) frames. Reset() between frames. */
    class Assembler(private val onFrame: (kind: Byte, message: ByteArray) -> Unit) {
        private var buffer: MutableMap<Int, ByteArray>? = null
        private var lastSeq = -1

        fun onChunk(chunk: ByteArray): Boolean {
            if (chunk.size < CHUNK_HEADER) return false
            val flags = chunk[0].toInt() and 0xff
            val seq = chunk[1].toInt() and 0xff
            val len = Bytes.intFrom16(chunk[2], chunk[3])
            if (4 + len > chunk.size) return false
            val data = chunk.copyOfRange(CHUNK_HEADER, CHUNK_HEADER + len)

            if ((flags and FLAG_FIRST) != 0) {
                buffer = HashMap()
                lastSeq = -1
            }
            val buf = buffer ?: return false
            buf[seq] = data
            lastSeq = maxOf(lastSeq, seq)

            if ((flags and FLAG_LAST) != 0) {
                val total = lastSeq + 1
                if (buf.size != total) {
                    buffer = null
                    return false
                }
                val frame = ByteArray((0 until total).sumOf { buf[it]!!.size })
                var p = 0
                for (i in 0 until total) {
                    val part = buf[i]!!
                    System.arraycopy(part, 0, frame, p, part.size)
                    p += part.size
                }
                buffer = null
                if (frame.isEmpty()) return false
                val kind = frame[0]
                val message = frame.copyOfRange(1, frame.size)
                onFrame(kind, message)
                return true
            }
            return true
        }

        fun reset() {
            buffer = null
            lastSeq = -1
        }
    }
}
