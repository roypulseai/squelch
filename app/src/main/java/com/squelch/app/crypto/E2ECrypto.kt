package com.squelch.app.crypto

import android.util.Base64
import android.util.Log
import com.squelch.app.crypto.noise.Hkdf
import com.squelch.app.util.Bytes
import com.squelch.app.util.toHex
import org.json.JSONObject

object E2ECrypto {
    private const val TAG = "E2ECrypto"

    fun encryptFor(
        senderIdentity: Identity,
        recipientXPubHex: String,
        plaintext: ByteArray
    ): String {
        val recipientXPub = Bytes.unhex(recipientXPubHex)
        val sharedSecret = X25519.dh(senderIdentity.xSecret, recipientXPub)
        val derivedKey = Hkdf.hash(sharedSecret + "squelch_msg_v1".toByteArray())
        val ciphertext = AesGcm.encrypt(derivedKey, plaintext)

        val envelope = JSONObject().apply {
            put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            put("sp", senderIdentity.edPub.toHex())
            put("xp", senderIdentity.xPub.toHex())
        }
        return envelope.toString()
    }

    fun decryptWithMyKey(
        myXSecret: ByteArray,
        envelopeJson: String
    ): Pair<String, ByteArray>? {
        return try {
            val envelope = JSONObject(envelopeJson)
            val ciphertext = Base64.decode(envelope.getString("ct"), Base64.NO_WRAP)
            val senderEdPubHex = envelope.getString("sp")
            val senderXPub = Bytes.unhex(envelope.getString("xp"))
            val sharedSecret = X25519.dh(myXSecret, senderXPub)
            val derivedKey = Hkdf.hash(sharedSecret + "squelch_msg_v1".toByteArray())
            val plaintext = AesGcm.decrypt(derivedKey, ciphertext)
            senderEdPubHex to plaintext
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }
}
