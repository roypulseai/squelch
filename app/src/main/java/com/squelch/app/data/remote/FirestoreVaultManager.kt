package com.squelch.app.data.remote

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.squelch.app.util.CompressionUtil
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreVaultManager @Inject constructor() {

    companion object {
        private const val TAG = "FirestoreVaultManager"
        private const val COLLECTION = "vaults"
        private const val FIELD_VAULT = "v"
        private const val FIELD_VAULT_LEGACY = "vault"
        private const val FIELD_UPDATED = "u"
        private const val FIELD_UPDATED_LEGACY = "updatedAt"
    }

    private val db = FirebaseFirestore.getInstance()

    suspend fun hasVault(googleUid: String): Boolean {
        return try {
            val doc = db.collection(COLLECTION).document(googleUid).get().await()
            doc.exists() && (doc.getString(FIELD_VAULT) != null || doc.getString(FIELD_VAULT_LEGACY) != null)
        } catch (e: Exception) {
            Log.e(TAG, "hasVault failed: ${e.message}", e)
            false
        }
    }

    suspend fun uploadVault(googleUid: String, ciphertext: ByteArray) {
        try {
            val compressed = CompressionUtil.compress(ciphertext)
            val b64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
            val data = mapOf(
                FIELD_VAULT to b64,
                FIELD_UPDATED to System.currentTimeMillis()
            )
            db.collection(COLLECTION).document(googleUid).set(data).await()
            Log.d(TAG, "Vault uploaded (${ciphertext.size} -> ${compressed.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "uploadVault failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun downloadVault(googleUid: String): ByteArray? {
        return try {
            val doc = db.collection(COLLECTION).document(googleUid).get().await()

            val compressed = doc.getString(FIELD_VAULT)
            if (compressed != null) {
                return CompressionUtil.decompress(Base64.decode(compressed, Base64.NO_WRAP))
            }

            val legacy = doc.getString(FIELD_VAULT_LEGACY)
            if (legacy != null) {
                val raw = Base64.decode(legacy, Base64.NO_WRAP)
                return raw
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "downloadVault failed: ${e.message}", e)
            null
        }
    }
}
