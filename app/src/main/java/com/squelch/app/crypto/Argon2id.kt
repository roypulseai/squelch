package com.squelch.app.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

object Argon2id {

    const val DEFAULT_PARALLELISM = 1
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_MEMORY_KB = 65_536

    private const val TYPE_ID = Argon2Parameters.ARGON2_id

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

        val gen = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(outBytes)
        gen.generateBytes(password, out)
        return out
    }
}
