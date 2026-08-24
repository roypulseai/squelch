package com.squelch.app.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.squelch.app.data.local.entity.ContactEntity
import com.squelch.app.util.CompressionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactSyncManager @Inject constructor() {

    companion object {
        private const val TAG = "ContactSyncManager"
        private const val FIELD = "contacts"
        private const val FIELD_V = "contacts_v"
    }

    private val db = FirebaseFirestore.getInstance()

    suspend fun pushContacts(googleUid: String, contacts: List<ContactEntity>) = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            for (c in contacts) {
                arr.put(JSONObject().apply {
                    put("k", c.pubkey)
                    put("u", c.firebaseUid)
                    put("x", c.xPub)
                    put("c", c.callsign)
                    put("n", c.displayName)
                    put("t", c.lastSeen)
                    put("e", c.email)
                    put("i", c.userId)
                })
            }
            val json = arr.toString().toByteArray(Charsets.UTF_8)
            val compressed = CompressionUtil.compressToBase64(json)
            val data = mapOf(
                FIELD_V to compressed
            )
            db.collection("users").document(googleUid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.d(TAG, "Pushed ${contacts.size} contacts to users/$googleUid (${compressed.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "pushContacts failed: ${e.message}", e)
        }
    }

    suspend fun pullContacts(googleUid: String): List<ContactEntity> = withContext(Dispatchers.IO) {
        try {
            val doc = db.collection("users").document(googleUid).get().await()

            val compressed = doc.getString(FIELD_V)
            if (compressed != null) {
                val json = String(CompressionUtil.decompressFromBase64(compressed), Charsets.UTF_8)
                return@withContext parseContacts(json)
            }

            val legacy = doc.getString(FIELD)
            if (legacy != null) {
                val contacts = parseContacts(legacy)
                if (contacts.isNotEmpty()) {
                    pushContacts(googleUid, contacts)
                }
                return@withContext contacts
            }

            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "pullContacts failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseContacts(json: String): List<ContactEntity> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ContactEntity(
                pubkey = obj.optString("k", obj.optString("pubkey", "")),
                firebaseUid = obj.optString("u", obj.optString("firebaseUid", "")),
                xPub = obj.optString("x", obj.optString("xPub", "")),
                callsign = obj.optString("c", obj.optString("callsign", "")),
                displayName = obj.optString("n", obj.optString("displayName", "")),
                lastSeen = obj.optLong("t", obj.optLong("lastSeen", 0)),
                email = obj.optString("e", obj.optString("email", "")),
                userId = obj.optString("i", obj.optString("userId", ""))
            )
        }
    }
}
