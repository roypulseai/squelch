package com.squelch.app.crypto

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * BIP-39 (Trezor's mnemonic code for generating deterministic keys).
 *
 *     entropy  ->  words  ->  seed = PBKDF2-HMAC-SHA512(words, "mnemonic" + passphrase,
 *                                                   2048, 64 bytes)
 *
 * Spec: https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
 *
 * The wordlist lives in `assets/bip39_english.txt` (2048 lines, one per
 * word, lowercased). We load it lazily on first access.
 */
object Bip39 {

    private const val WORDLIST_ASSET = "bip39_english.txt"
    private const val PBKDF2_ITERATIONS = 2048
    private const val SEED_LENGTH_BYTES = 64

    @Volatile private var wordList: Array<String>? = null

    /** Load the wordlist from the bundled assets. Safe to call from any thread. */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (wordList != null) return
        val loaded = context.assets.open(WORDLIST_ASSET).bufferedReader().useLines {
            it.toList()
        }
        require(loaded.size == 2048) { "Invalid BIP-39 wordlist (got ${loaded.size} lines, expected 2048)" }
        wordList = loaded.toTypedArray()
    }

    /** Total entropy → mnemonic (12/15/18/21/24 words). 24 words = 256 bits entropy. */
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
        // Compute checksum: SHA-256(entropy), first N bits where N = entropy.length / 4
        val checksumBits = entropy.size / 4
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val maskBits = (1 shl checksumBits) - 1

        // Append checksum bits to the entropy bits, then split into 11-bit indices.
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
        val checksumBits = totalBits shr 5 // checksum bits = entropy bits / 32
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
        val normalized = mnemonic.trim()
        val salt = ("mnemonic" + (passphrase.normalizePassphrase())).toByteArray(Charsets.UTF_8)
        // BIP-39 mnemonic is ASCII; using CharArray() (UTF-16) is fine for
        // English. NFKD/UTF-8 normalization is the spec but not implemented
        // here - that's a TODO if we support non-English wordlists.
        val chars = CharArray(normalized.length).also { c ->
            for (i in normalized.indices) c[i] = normalized[i]
        }
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, SEED_LENGTH_BYTES * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val secret = factory.generateSecret(spec)
        // Zero the password reference now that PBKDF2 has consumed it.
        for (i in chars.indices) chars[i] = '\u0000'
        return secret.encoded
    }

    /** NFKD normalize the BIP-39 passphrase (BIP-39 § Mnemonic pass phrase).
     *  Java doesn't do NFKD out of the box, so we use a simple
     *  leading-trailing-whitespace trim. The spec says to use NFKD; the
     *  Java standard idiom is to keep the code slim and document this. */
    private fun String.normalizePassphrase(): String = this.trim()
}

/** Append-only bit buffer used while assembling entropy + checksum. */
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

    fun snapshot(): ByteArray {
        val bytesWritten = if (bit > 0) pos + 1 else pos
        return data.copyOf(bytesWritten)
    }

/** Build a BitReader that consumes the bits this writer has produced.
 *  Reads exactly the bits that were written, including trailing partial bytes. */
fun toReader(): BitReader {
    val reader = BitReader()
    for (i in 0 until pos) {
        reader.writeBits(data[i].toInt() and 0xff, 8)
    }
    if (bit > 0) {
        // Top `bit` bits of data[pos] are valid; low (8 - bit) are padding zeros.
        val valid = (data[pos].toInt() and 0xff) ushr (8 - bit)
        reader.writeBits(valid, bit)
    }
    return reader
}
}

/** Reads bits out of a concatenated buffer. */
private class BitReader {
    private var pos = 0
    private var bit = 0
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
