package com.squelch.app.util

object Bytes {
    private val HEX = "0123456789abcdef".toCharArray()

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    fun unhex(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(hex[i * 2], 16) shl 4) or
                Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    fun u16be(v: Int): ByteArray = byteArrayOf(
        ((v ushr 8) and 0xff).toByte(),
        (v and 0xff).toByte()
    )

    fun u32be(v: Long): ByteArray = byteArrayOf(
        ((v ushr 24) and 0xff).toByte(),
        ((v ushr 16) and 0xff).toByte(),
        ((v ushr 8) and 0xff).toByte(),
        (v and 0xff).toByte()
    )

    fun u64be(v: Long): ByteArray {
        val out = ByteArray(8)
        for (i in 0 until 8) out[i] = ((v ushr (56 - i * 8)) and 0xff).toByte()
        return out
    }

    fun concat(vararg arrays: ByteArray): ByteArray {
        var total = 0
        for (a in arrays) total += a.size
        val out = ByteArray(total)
        var pos = 0
        for (a in arrays) {
            System.arraycopy(a, 0, out, pos, a.size)
            pos += a.size
        }
        return out
    }

    fun zeros(n: Int): ByteArray = ByteArray(n)

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun randomId(secureRandom: java.security.SecureRandom, n: Int = 16): ByteArray {
        val out = ByteArray(n)
        secureRandom.nextBytes(out)
        return out
    }
}
