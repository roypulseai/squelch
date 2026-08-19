package com.squelch.app.data.remote

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreVaultManager @Inject constructor() {

    companion object {
        private const val TAG = "FirestoreVaultManager"
        private const val COLLECTION = "vaults"
        private const val FIELD_VAULT = "vault"
        private const val FIELD_UPDATED = "updatedAt"
    }

    private val db = FirebaseFirestore.getInstance()

    suspend fun hasVault(googleUid: String): Boolean {
        return try {
            val doc = db.collection(COLLECTION).document(googleUid).get().await()
            doc.exists() && doc.getString(FIELD_VAULT) != null
        } catch (e: Exception) {
            Log.e(TAG, "hasVault failed: ${e.message}", e)
            false
        }
    }

    suspend fun uploadVault(googleUid: String, ciphertext: ByteArray) {
        try {
            val b64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            val data = mapOf(
                FIELD_VAULT to b64,
                FIELD_UPDATED to com.google.firebase.Timestamp.now()
            )
            db.collection(COLLECTION).document(googleUid).set(data).await()
            Log.d(TAG, "Vault uploaded for $googleUid")
        } catch (e: Exception) {
            Log.e(TAG, "uploadVault failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun downloadVault(googleUid: String): ByteArray? {
        return try {
            val doc = db.collection(COLLECTION).document(googleUid).get().await()
            val b64 = doc.getString(FIELD_VAULT) ?: return null
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "downloadVault failed: ${e.message}", e)
            null
        }
    }
}
