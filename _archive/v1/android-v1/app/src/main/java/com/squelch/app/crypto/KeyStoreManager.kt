package com.squelch.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the identity seed blob encrypted at rest with a device-bound
 * Android Keystore AES-256-GCM key. The raw seed never touches disk in plaintext.
 */
class KeyStoreManager(context: Context) {
    private val prefs = context.getSharedPreferences("squelch_secure", Context.MODE_PRIVATE)
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY_ALIAS = "squelch_identity_key"
        private const val PREF_BLOB = "identity_blob_v1"
    }

    private fun getOrCreateKey(): SecretKey {
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun saveBlob(blob: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(blob)
        val payload = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(ct, 0, payload, iv.size, ct.size)
        prefs.edit().putString(PREF_BLOB, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun loadBlob(): ByteArray? {
        val encoded = prefs.getString(PREF_BLOB, null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val ct = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    fun hasBlob(): Boolean = prefs.contains(PREF_BLOB)

    fun wipeBlob() {
        prefs.edit().remove(PREF_BLOB).apply()
    }
}
