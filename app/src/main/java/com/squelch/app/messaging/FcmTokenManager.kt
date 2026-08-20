package com.squelch.app.messaging

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

object FcmTokenManager {
    private const val TAG = "FcmTokenManager"
    private val firestore = FirebaseFirestore.getInstance()

    fun registerToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .update("fcmToken", token, "fcmUpdatedAt", com.google.firebase.Timestamp.now())
            .addOnSuccessListener { Log.d(TAG, "FCM token registered for $uid") }
            .addOnFailureListener {
                firestore.collection("users").document(uid)
                    .set(
                        mapOf("fcmToken" to token, "fcmUpdatedAt" to com.google.firebase.Timestamp.now()),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener { Log.d(TAG, "FCM token created for $uid") }
                    .addOnFailureListener { e -> Log.e(TAG, "FCM token failed: ${e.message}") }
            }
    }
}
