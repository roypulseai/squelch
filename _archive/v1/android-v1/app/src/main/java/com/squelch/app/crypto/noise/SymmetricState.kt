package com.squelch.app.crypto.noise

import com.squelch.app.util.Bytes

/** Noise SymmetricState (spec 5.2): CipherState + chaining key + handshake hash. */
class SymmetricState {
    private var ck: ByteArray = ByteArray(0)
    private var h: ByteArray = ByteArray(0)
    private var cs = CipherState()

    fun initializeSymmetric(protocolName: ByteArray) {
        h = if (protocolName.size <= Hkdf.HASHLEN) {
            val padded = ByteArray(Hkdf.HASHLEN)
            System.arraycopy(protocolName, 0, padded, 0, protocolName.size)
            padded
        } else {
            Hkdf.hash(protocolName)
        }
        ck = h
        cs = CipherState()
    }

    fun mixKey(inputKeyMaterial: ByteArray) {
        val outputs = Hkdf.hkdf(ck, inputKeyMaterial, 2)
        ck = outputs[0]
        cs.rekeyWithNew(outputs[1])
    }

    fun mixHash(data: ByteArray) {
        h = Hkdf.hash(Bytes.concat(h, data))
    }

    fun mixKeyAndHash(inputKeyMaterial: ByteArray) {
        val outputs = Hkdf.hkdf(ck, inputKeyMaterial, 3)
        ck = outputs[0]
        mixHash(outputs[1])
        cs.rekeyWithNew(outputs[2])
    }

    fun getHandshakeHash(): ByteArray = h

    fun hasKey(): Boolean = cs.hasKey()

    fun encryptAndHash(plaintext: ByteArray): ByteArray {
        val ct = cs.encryptWithAd(h, plaintext)
        mixHash(ct)
        return ct
    }

    fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        val pt = cs.decryptWithAd(h, ciphertext)
        mixHash(ciphertext)
        return pt
    }

    fun split(): Pair<CipherState, CipherState> {
        val outputs = Hkdf.hkdf(ck, ByteArray(0), 2)
        return CipherState(outputs[0]) to CipherState(outputs[1])
    }
}
