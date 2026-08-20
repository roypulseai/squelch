package com.squelch.app.messaging

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.squelch.app.crypto.Identity
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.dao.ContactDao
import com.squelch.app.util.toHex
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit

class MessagePollWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MsgPollWorker"
        const val WORK_NAME = "squelch_message_poll"
    }

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()
        val identity = Identity.fromGoogleUid(uid)
        val edPubHex = identity.edPub.toHex()

        return try {
            val db = com.squelch.app.messaging.MessageRelayHolder.database
            if (db == null) {
                Log.d(TAG, "No database available, skipping poll")
                return Result.success()
            }

            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("messages")
                .whereEqualTo("recipient", edPubHex)
                .get()
                .await()

            var newCount = 0
            for (doc in snapshot.documents) {
                val sender = doc.getString("sender") ?: continue
                if (sender == edPubHex) {
                    doc.reference.delete()
                    continue
                }

                val payloadB64 = doc.getString("payload") ?: continue
                val body = try {
                    android.util.Base64.decode(payloadB64, android.util.Base64.NO_WRAP)
                        .toString(Charsets.UTF_8)
                } catch (_: Exception) { continue }

                val senderName = doc.getString("senderName") ?: sender.take(8)
                val conversationId = sender

                val msg = MessageEntity(
                    conversationId = conversationId,
                    msgId = UUID.randomUUID().toString(),
                    sender = sender,
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

                doc.reference.delete()
                newCount++
            }

            if (newCount > 0) {
                showPollNotification(newCount)
            }

            Log.d(TAG, "Poll complete: $newCount new messages")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Poll failed: ${e.message}")
            Result.retry()
        }
    }

    private fun showPollNotification(count: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = android.content.Intent(applicationContext, com.squelch.app.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            applicationContext, 9999, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, com.squelch.app.util.Notifications.CHANNEL_MESSAGES)
            .setSmallIcon(com.squelch.app.R.drawable.ic_launcher_foreground)
            .setContentTitle("Squelch")
            .setContentText("You have $count new message${if (count > 1) "s" else ""}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(9999, notification)
    }
}

object MessagePollScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MessagePollWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MessagePollWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d("MsgPollScheduler", "Message poll scheduled (15 min interval)")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MessagePollWorker.WORK_NAME)
    }
}
