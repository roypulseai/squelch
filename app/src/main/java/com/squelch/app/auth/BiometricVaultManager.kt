package com.squelch.app.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricVaultManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BiometricVaultManager"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "squelch_vault_bio_key"
        private const val PREFS = "squelch_biometric_vault"
        private const val KEY_ENC_KDB = "enc_kdb"
        private const val KEY_IV = "enc_iv"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isLockEnabled(): Boolean = prefs.getBoolean("lock_enabled", false)

    fun setLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("lock_enabled", enabled).apply()
    }

    fun hasCachedKey(): Boolean = prefs.contains(KEY_ENC_KDB)

    private fun getOrCreateKey(): SecretKey {
        keyStore.getEntry(KEY_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        return KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, KEYSTORE
        ).apply { init(spec) }.generateKey()
    }

    fun getEncryptionCipher(): Cipher {
        val key = getOrCreateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    fun getDecryptionCipher(): Cipher {
        val ivBase64 = prefs.getString(KEY_IV, null)
            ?: throw IllegalStateException("No IV stored")
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val key = getOrCreateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
    }

    fun saveEncryptedKey(cipher: Cipher, kDb: ByteArray) {
        try {
            val encrypted = cipher.doFinal(kDb)
            prefs.edit()
                .putString(KEY_ENC_KDB, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putBoolean("lock_enabled", true)
                .apply()
            Log.d(TAG, "Saved encrypted kDb locally")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save kDb: ${e.message}", e)
            throw e
        }
    }

    fun decryptKey(cipher: Cipher): ByteArray {
        try {
            val encBase64 = prefs.getString(KEY_ENC_KDB, null)
                ?: throw IllegalStateException("No encrypted kDb stored")
            val encrypted = Base64.decode(encBase64, Base64.NO_WRAP)
            return cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt kDb: ${e.message}", e)
            throw e
        }
    }

    fun clearLocalKey() {
        prefs.edit().clear().apply()
    }
}
