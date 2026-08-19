package com.squelch.app.crypto

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Bip39 {

    private const val WORDLIST_ASSET = "bip39_english.txt"
    private const val PBKDF2_ITERATIONS = 2048
    private const val SEED_LENGTH_BYTES = 64

    @Volatile private var wordList: Array<String>? = null

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (wordList != null) return
        val loaded = context.assets.open(WORDLIST_ASSET).bufferedReader().useLines {
            it.toList()
        }
        require(loaded.size == 2048) { "Invalid BIP-39 wordlist (got ${loaded.size} lines, expected 2048)" }
        wordList = loaded.toTypedArray()
    }

    fun generateMnemonic(context: Context, entropyBytes: Int = 32, random: SecureRandom = SecureRandom()): String {
        require(entropyBytes in setOf(16, 20, 24, 28, 32)) { "entropyBytes must be one of 16/20/24/28/32" }
        val entropy = ByteArray(entropyBytes).also { random.nextBytes(it) }
        return entropyToMnemonic(context, entropy)
    }

    fun entropyToMnemonic(context: Context, entropy: ByteArray): String {
        ensureLoaded(context)
        val words = wordList ?: error("Bip39 wordlist not loaded")
        if (entropy.size < 16 || entropy.size > 32 || entropy.size % 4 != 0) {
            throw IllegalArgumentException("bad entropy length: ${entropy.size}")
        }
        val checksumBits = entropy.size / 4
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val maskBits = (1 shl checksumBits) - 1

        val bits = BitWriter()
        bits.writeBytes(entropy)
        bits.writeBits(maskBits, checksumBits)

        val indices = IntArray((entropy.size * 8 + checksumBits) / 11)
        val reader = bits.toReader()
        for (i in indices.indices) {
            indices[i] = reader.readBits(11)
        }
        return buildString {
            for (i in indices.indices) {
                if (i > 0) append(' ')
                append(words[indices[i]])
            }
        }
    }

    fun validateMnemonic(context: Context, mnemonic: String): Boolean {
        ensureLoaded(context)
        val words = wordList ?: return false
        val parts = mnemonic.trim().split(Regex("\\s+"))
        if (parts.size !in setOf(12, 15, 18, 21, 24)) return false

        val totalBits = parts.size * 11
        val checksumBits = totalBits shr 5
        val entropyBits = totalBits - checksumBits
        if (entropyBits % 8 != 0) return false
        val entropyBytes = entropyBits / 8

        val reader = BitReader()
        for (word in parts) {
            val idx = words.indexOf(word)
            if (idx < 0) return false
            reader.writeBits(idx, 11)
        }
        val entropy = reader.readBytes(entropyBytes)
        val storedChecksum = reader.readBits(checksumBits)
        val computedChecksum = firstNBits(MessageDigest.getInstance("SHA-256").digest(entropy), checksumBits)
        return storedChecksum == computedChecksum
    }

    fun mnemonicToSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val normalized = Normalizer.normalize(mnemonic.trim(), Normalizer.Form.NFKD)
        val salt = ("mnemonic" + Normalizer.normalize(passphrase, Normalizer.Form.NFKD)).toByteArray(Charsets.UTF_8)
        val chars = CharArray(normalized.length).also { c ->
            for (i in normalized.indices) c[i] = normalized[i]
        }
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, SEED_LENGTH_BYTES * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val secret = factory.generateSecret(spec)
        for (i in chars.indices) chars[i] = '\u0000'
        return secret.encoded
    }
}

private class BitWriter {
    private val data = ByteArray(256)
    private var pos = 0
    private var bit = 0

    fun writeBytes(bytes: ByteArray) {
        for (b in bytes) writeBits(b.toInt() and 0xff, 8)
    }

    fun writeBits(value: Int, n: Int) {
        var v = value and ((1 shl n) - 1)
        var remaining = n
        while (remaining > 0) {
            val room = 8 - bit
            val take = if (remaining > room) room else remaining
            val shift = remaining - take
            val mask = (1 shl take) - 1
            val bits = (v ushr shift) and mask
            data[pos] = ((data[pos].toInt() shl take) and 0xff or bits).toByte()
            bit += take
            if (bit == 8) { pos++; bit = 0 }
            remaining -= take
        }
    }

    fun toReader(): BitReader {
        val reader = BitReader()
        for (i in 0 until pos) {
            reader.writeBits(data[i].toInt() and 0xff, 8)
        }
        if (bit > 0) {
            val valid = (data[pos].toInt() and 0xff) ushr (8 - bit)
            reader.writeBits(valid, bit)
        }
        return reader
    }
}

private class BitReader {
    private var pos = 0
    private val bits = mutableListOf<Int>()

    fun writeBits(value: Int, n: Int) {
        var v = value
        for (i in n - 1 downTo 0) {
            bits.add((v shr i) and 1)
        }
    }

    fun readBits(n: Int): Int {
        var v = 0
        for (i in 0 until n) v = v shl 1 or bits[pos++]
        return v
    }

    fun readBytes(count: Int): ByteArray {
        val out = ByteArray(count)
        for (i in 0 until count) {
            var v = 0
            for (j in 0 until 8) v = v shl 1 or bits[pos++]
            out[i] = v.toByte()
        }
        return out
    }
}

private fun firstNBits(bytes: ByteArray, n: Int): Int {
    var v = 0
    var remaining = n
    var idx = 0
    while (remaining > 0) {
        val take = if (remaining >= 8) 8 else remaining
        val current = bytes[idx].toInt() and 0xff
        val shift = 8 - take
        val mask = (1 shl take) - 1
        v = (v shl take) or ((current ushr shift) and mask)
        remaining -= take
        idx++
    }
    return v
}
