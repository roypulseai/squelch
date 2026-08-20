package com.squelch.app.messaging

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.squelch.app.MainActivity
import com.squelch.app.R
import com.squelch.app.crypto.Identity
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.util.Notifications
import com.squelch.app.util.toHex
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessageForegroundService : Service() {

    companion object {
        private const val TAG = "MsgForegroundSvc"
        private const val NOTIFICATION_ID = 7777
        private const val CHANNEL_ID = "squelch_service"
        private const val POLL_INTERVAL_MS = 30_000L

        private var instance: MessageForegroundService? = null
        val isRunning: Boolean get() = instance != null

        fun start(context: android.content.Context) {
            val intent = Intent(context, MessageForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, MessageForegroundService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: ListenerRegistration? = null
    private var pollJob: Job? = null
    private var selfEdPubHex: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring for messages"))
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val edPubHex = intent?.getStringExtra("edPubHex")
        if (edPubHex != null) {
            selfEdPubHex = edPubHex
            startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        listener = null
        pollJob?.cancel()
        scope.cancel()
        instance = null
        Log.d(TAG, "Service destroyed")
    }

    private fun startListening() {
        val db = MessageRelayHolder.database ?: run {
            Log.e(TAG, "No database, will retry")
            pollJob = scope.launch {
                delay(5000)
                startListening()
            }
            return
        }

        val firestore = FirebaseFirestore.getInstance()

        listener?.remove()
        listener = firestore.collection("messages")
            .whereEqualTo("recipient", selfEdPubHex)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listener error: ${error.message}")
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents
                if (docs.isNullOrEmpty()) return@addSnapshotListener
                Log.d(TAG, "Live: ${docs.size} messages")

                scope.launch {
                    val blocked = try { db.blocked().allPubkeys() } catch (_: Exception) { emptyList() }
                    for (doc in docs) {
                        try {
                            val sender = doc.getString("sender") ?: continue
                            if (sender == selfEdPubHex) {
                                doc.reference.delete()
                                continue
                            }
                            if (sender in blocked) {
                                doc.reference.delete()
                                continue
                            }
                            val payloadB64 = doc.getString("payload") ?: continue
                            val body = Base64.decode(payloadB64, Base64.NO_WRAP).toString(Charsets.UTF_8)
                            val senderName = doc.getString("senderName") ?: sender.take(8)
                            val conversationId = sender

                            storeMessage(db, conversationId, sender, senderName, body)
                            showNewMessageNotification(senderName, body, conversationId)

                            doc.reference.delete()
                        } catch (e: Exception) {
                            Log.e(TAG, "Process doc failed: ${e.message}")
                        }
                    }
                }
            }

        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                try {
                    val snapshot = firestore.collection("messages")
                        .whereEqualTo("recipient", selfEdPubHex)
                        .get()
                        .await()

                    val docs = snapshot.documents.toList()
                    for (doc in docs) {
                        val sender = doc.getString("sender") ?: continue
                        if (sender == selfEdPubHex) {
                            doc.reference.delete()
                            continue
                        }
                        val blockedPoll = try { db.blocked().allPubkeys() } catch (_: Exception) { emptyList() }
                        if (sender in blockedPoll) {
                            doc.reference.delete()
                            continue
                        }
                        val payloadB64 = doc.getString("payload") ?: continue
                        val body = Base64.decode(payloadB64, Base64.NO_WRAP).toString(Charsets.UTF_8)
                        val senderName = doc.getString("senderName") ?: sender.take(8)

                        scope.launch {
                            storeMessage(db, sender, sender, senderName, body)
                            showNewMessageNotification(senderName, body, sender)
                        }
                        doc.reference.delete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Poll failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun storeMessage(
        db: SquelchDatabase,
        conversationId: String,
        sender: String,
        senderName: String,
        body: String
    ) {
        withContext(Dispatchers.IO) {
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

            val existing = db.conversations().get(conversationId)
            if (existing == null) {
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
        }
    }

    private fun showNewMessageNotification(title: String, body: String, conversationId: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val id = conversationId.hashCode()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
        }
        val pending = PendingIntent.getActivity(
            this, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        nm.notify(id, notification)
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Squelch Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Squelch running for instant message delivery"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Squelch")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
