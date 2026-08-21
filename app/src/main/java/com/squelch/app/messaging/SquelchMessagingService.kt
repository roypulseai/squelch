package com.squelch.app.messaging

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
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
        val type = data["type"] ?: "message"

        when (type) {
            "message" -> {
                val senderEdPub = data["senderEdPub"] ?: return
                val senderName = data["senderName"] ?: senderEdPub.take(8)
                val body = data["body"] ?: return
                val conversationId = data["conversationId"] ?: senderEdPub

                showNotification(senderName, body, conversationId)

                val db = MessageRelayHolder.database ?: return
                scope.launch {
                    try {
                        val msg = MessageEntity(
                            conversationId = conversationId,
                            msgId = UUID.randomUUID().toString(),
                            sender = senderEdPub,
                            body = body,
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
                                    lastMessagePreview = body.take(80),
                                    lastMessageTimestamp = msg.timestamp,
                                    unreadCount = 1
                                )
                            )
                        } else {
                            db.conversations().updateLastMessage(
                                id = conversationId,
                                preview = body.take(80),
                                timestamp = msg.timestamp
                            )
                            db.conversations().incrementUnread(conversationId)
                        }
                        Log.d(TAG, "Stored push message from $senderName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Store push message failed: ${e.message}")
                    }
                }
            }
            "delivery_ack" -> {
                val msgId = data["msgId"] ?: return
                val db = MessageRelayHolder.database ?: return
                scope.launch {
                    try { db.messages().updateDelivery(msgId, 1) } catch (_: Exception) {}
                }
            }
            "read_ack" -> {
                val msgId = data["msgId"] ?: return
                val db = MessageRelayHolder.database ?: return
                scope.launch {
                    try { db.messages().markRead(msgId, System.currentTimeMillis()) } catch (_: Exception) {}
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

            val defaultSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

            val notification = androidx.core.app.NotificationCompat.Builder(this, com.squelch.app.util.Notifications.CHANNEL_MESSAGES)
                .setSmallIcon(com.squelch.app.R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setSound(defaultSoundUri)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
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
    var database: com.squelch.app.data.local.SquelchDatabase? = null
}
