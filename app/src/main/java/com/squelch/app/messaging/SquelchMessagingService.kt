package com.squelch.app.messaging

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.squelch.app.crypto.E2ECrypto
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.VaultSession
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class SquelchMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(20)}...")
        FcmTokenManager.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Push received: ${message.data}")

        val data = message.data
        val senderEdPubHex = data["senderEdPub"] ?: return
        val payloadCt = data["payload"] ?: return
        val conversationId = data["conversationId"] ?: senderEdPubHex
        val senderName = data["senderName"] ?: senderEdPubHex.take(8)
        val type = data["type"] ?: "message"

        when (type) {
            "message" -> {
                val title = data["title"] ?: senderName
                val body = data["body"] ?: "New message"
                showNotification(title, body, conversationId)

                val db = MessageRelayHolder.database ?: return
                scope.launch {
                    try {
                        val plaintext = payloadCt.toByteArray(Charsets.UTF_8)
                        val msg = MessageEntity(
                            conversationId = conversationId,
                            msgId = UUID.randomUUID().toString(),
                            sender = senderEdPubHex,
                            body = String(plaintext, Charsets.UTF_8),
                            timestamp = System.currentTimeMillis(),
                            direction = 0,
                            delivery = 1,
                            kind = 2
                        )
                        db.messages().insert(msg)

                        val existingConv = db.conversations().get(conversationId)
                        if (existingConv == null) {
                            db.conversations().upsert(
                                ConversationEntity(
                                    id = conversationId,
                                    name = senderName,
                                    lastMessagePreview = String(plaintext, Charsets.UTF_8).take(80),
                                    lastMessageTimestamp = msg.timestamp,
                                    unreadCount = 1
                                )
                            )
                        } else {
                            db.conversations().updateLastMessage(
                                id = conversationId,
                                preview = String(plaintext, Charsets.UTF_8).take(80),
                                timestamp = msg.timestamp
                            )
                            db.conversations().incrementUnread(conversationId)
                        }
                        Log.d(TAG, "Message stored from push: $senderName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to store push message: ${e.message}")
                    }
                }
            }
            "delivery_ack" -> {
                val msgId = data["msgId"] ?: return
                val db = MessageRelayHolder.database ?: return
                scope.launch {
                    try {
                        db.messages().updateDelivery(msgId, 1)
                        Log.d(TAG, "Delivery acknowledged for $msgId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update delivery: ${e.message}")
                    }
                }
            }
            "read_ack" -> {
                val msgId = data["msgId"] ?: return
                val db = MessageRelayHolder.database ?: return
                scope.launch {
                    try {
                        db.messages().markRead(msgId, System.currentTimeMillis())
                        Log.d(TAG, "Read acknowledged for $msgId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update read: ${e.message}")
                    }
                }
            }
        }
    }

    private fun showNotification(title: String, body: String, conversationId: String) {
        try {
            val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val id = conversationId.hashCode()

            val intent = android.content.Intent(this, com.squelch.app.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("conversationId", conversationId)
            }
            val pending = android.app.PendingIntent.getActivity(
                this, id, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(this, com.squelch.app.util.Notifications.CHANNEL_MESSAGES)
                .setSmallIcon(com.squelch.app.R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()

            nm.notify(id, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Notification failed: ${e.message}")
        }
    }
}

object MessageRelayHolder {
    var database: SquelchDatabase? = null
}

object FcmTokenManager {
    private const val TAG = "FcmTokenManager"
    private val firestore = FirebaseFirestore.getInstance()

    fun registerToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .update("fcmToken", token, "fcmUpdatedAt", com.google.firebase.Timestamp.now())
            .addOnSuccessListener { Log.d(TAG, "FCM token registered for $uid") }
            .addOnFailureListener { e ->
                Log.e(TAG, "FCM token registration failed: ${e.message}")
                firestore.collection("users").document(uid)
                    .set(
                        mapOf("fcmToken" to token, "fcmUpdatedAt" to com.google.firebase.Timestamp.now()),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            }
    }

    fun sendPushNotification(
        recipientUid: String,
        senderEdPubHex: String,
        senderName: String,
        body: String,
        payloadCt: String,
        conversationId: String,
        type: String = "message"
    ) {
        firestore.collection("users").document(recipientUid)
            .get()
            .addOnSuccessListener { doc ->
                val fcmToken = doc.getString("fcmToken")
                if (fcmToken == null) {
                    Log.w(TAG, "No FCM token for $recipientUid")
                    return@addOnSuccessListener
                }

                val message = mapOf(
                    "token" to fcmToken,
                    "data" to mapOf(
                        "senderEdPub" to senderEdPubHex,
                        "senderName" to senderName,
                        "body" to body,
                        "payload" to payloadCt,
                        "conversationId" to conversationId,
                        "type" to type
                    )
                )

                firestore.collection("push_queue").add(message)
                    .addOnSuccessListener { Log.d(TAG, "Push queued for $recipientUid") }
                    .addOnFailureListener { e -> Log.e(TAG, "Push queue failed: ${e.message}") }
            }
    }

    fun sendDeliveryAck(senderUid: String, msgId: String) {
        firestore.collection("users").document(senderUid)
            .get()
            .addOnSuccessListener { doc ->
                val fcmToken = doc.getString("fcmToken") ?: return@addOnSuccessListener
                val message = mapOf(
                    "token" to fcmToken,
                    "data" to mapOf(
                        "type" to "delivery_ack",
                        "msgId" to msgId
                    )
                )
                firestore.collection("push_queue").add(message)
            }
    }

    fun sendReadAck(senderUid: String, msgId: String) {
        firestore.collection("users").document(senderUid)
            .get()
            .addOnSuccessListener { doc ->
                val fcmToken = doc.getString("fcmToken") ?: return@addOnSuccessListener
                val message = mapOf(
                    "token" to fcmToken,
                    "data" to mapOf(
                        "type" to "read_ack",
                        "msgId" to msgId
                    )
                )
                firestore.collection("push_queue").add(message)
            }
    }
}
