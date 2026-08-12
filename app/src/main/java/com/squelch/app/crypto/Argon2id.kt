package com.squelch.app.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id KDF (RFC 9106). Wraps BouncyCastle's
 * [org.bouncycastle.crypto.generators.Argon2BytesGenerator] so the rest
 * of the v2 code can stay free of BC's class names.
 *
 * Default parameters per the v2 spec:
 *   memory = 64 MiB  (65536 KiB)
 *   iterations = 3
 *   parallelism = 1
 *
 * These are dev/workstation-class settings; for production we may want
 * to lift memory to 96 MiB to better match phone-class CPUs.
 */
object Argon2id {

    const val DEFAULT_PARALLELISM = 1
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_MEMORY_KB = 65_536 // 64 MiB in KiB

    private const val TYPE_ID = Argon2Parameters.ARGON2_id

    /**
     * @param password  arbitrary length; UTF-8 encoded.
     * @param salt      16 bytes (recommended minimum per RFC 9106 §4).
     * @param outBytes  32 in v2 (then SHA-256'd by the caller for K_vault).
     */
    fun derive(
        password: ByteArray,
        salt: ByteArray,
        outBytes: Int = 32,
        iterations: Int = DEFAULT_ITERATIONS,
        parallelism: Int = DEFAULT_PARALLELISM,
        memoryKb: Int = DEFAULT_MEMORY_KB
    ): ByteArray {
        val params = Argon2Parameters.Builder(TYPE_ID)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withMemoryAsKB(memoryKb)
            .build()

        val gen = Argon2BytesGenerator().apply {
            init(params)
        }
        val out = ByteArray(outBytes)
        gen.generateBytes(password, out)
        return out
    }
}
