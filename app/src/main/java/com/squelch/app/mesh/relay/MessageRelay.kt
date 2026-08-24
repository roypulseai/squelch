package com.squelch.app.mesh.relay

import android.content.Context
import android.util.Log
import com.squelch.app.crypto.E2ECrypto
import com.squelch.app.crypto.Identity
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.messaging.MessageRelayHolder
import com.squelch.app.mesh.transport.FirestoreTransport
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.util.Notifications
import com.squelch.app.util.toHex
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRelay @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "MessageRelay"
        private const val MSG_TTL_HOURS = 24L
    }

    data class BlockEvent(val peerEdPubHex: String, val blocked: Boolean)

    val blockEvents = Channel<BlockEvent>(Channel.BUFFERED)

    private var transport: FirestoreTransport? = null
    private var scope: CoroutineScope? = null
    private var incomingJob: kotlinx.coroutines.Job? = null
    private var database: SquelchDatabase? = null

    var selfEdPubHex: String = ""
        private set
    var selfIdentity: Identity? = null
        private set
    var selfEmail: String = ""
        private set

    val isRunning: Boolean get() = transport != null

    suspend fun getContactName(db: SquelchDatabase): String {
        return try {
            val contact = db.contacts().get(selfEdPubHex)
            contact?.displayName?.ifEmpty { contact.callsign.ifEmpty { selfEdPubHex.take(8) } }
                ?: selfEdPubHex.take(8)
        } catch (_: Exception) {
            selfEdPubHex.take(8)
        }
    }

    fun start(edPubHex: String, db: SquelchDatabase, identity: Identity, email: String = "") {
        if (transport != null) {
            Log.d(TAG, "Already running for $edPubHex")
            return
        }
        selfEdPubHex = edPubHex
        selfIdentity = identity
        selfEmail = email
        database = db
        MessageRelayHolder.database = db
        Log.d(TAG, "Starting relay for self=$edPubHex")

        try {
            val t = FirestoreTransport(edPubHex)
            transport = t
            t.start()

            val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = s
            incomingJob = s.launch {
                Log.d(TAG, "Collecting incoming frames...")
                t.incoming.collect { frame ->
                    Log.d(TAG, "Frame from ${frame.senderEdPubHex}, kind=${frame.kind}, size=${frame.payload.size}")
                    try {
                        handleIncoming(frame.senderEdPubHex, frame.payload, frame.senderName, frame.senderEmail, db, frame.msgId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Handle incoming failed: ${e.message}", e)
                    }
                }
            }
            s.launch { cleanupOldMessages() }
            Log.d(TAG, "MessageRelay started for $edPubHex")
        } catch (e: Exception) {
            Log.e(TAG, "Start failed: ${e.message}", e)
        }
    }

    fun stop() {
        incomingJob?.cancel()
        incomingJob = null
        transport?.stop()
        transport = null
        scope?.cancel()
        scope = null
        database = null
        MessageRelayHolder.database = null
        Log.d(TAG, "MessageRelay stopped")
    }

    fun sendMessage(
        recipientEdPubHex: String,
        recipientUid: String,
        senderName: String,
        plaintext: String,
        msgId: String? = null
    ) {
        val t = transport ?: run {
            Log.e(TAG, "Transport not running, cannot send")
            return
        }

        scope?.launch {
            val identity = selfIdentity ?: run {
                Log.e(TAG, "No identity, cannot send")
                return@launch
            }
            val payload = encryptPayload(identity, recipientEdPubHex, recipientUid, plaintext.toByteArray(Charsets.UTF_8))

            t.sendWithMeta(
                recipientEdPubHex = recipientEdPubHex,
                recipientUid = recipientUid,
                senderName = senderName,
                senderEmail = selfEmail,
                payload = payload,
                msgId = msgId
            )
            Log.d(TAG, "Message sent to $recipientEdPubHex")
        }
    }

    fun sendCommand(
        recipientEdPubHex: String,
        recipientUid: String,
        senderName: String,
        kind: Int,
        payloadBytes: ByteArray
    ) {
        val t = transport ?: run {
            Log.e(TAG, "Transport not running, cannot send command")
            return
        }
        scope?.launch {
            val identity = selfIdentity ?: return@launch
            val payload = encryptPayload(identity, recipientEdPubHex, recipientUid, payloadBytes)

            t.sendWithMeta(
                recipientEdPubHex = recipientEdPubHex,
                recipientUid = recipientUid,
                senderName = senderName,
                senderEmail = selfEmail,
                payload = payload,
                kind = kind
            )
            Log.d(TAG, "Command sent (kind=$kind) to $recipientEdPubHex")
        }
    }

    private val xPubCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun encryptPayload(
        identity: Identity,
        recipientEdPubHex: String,
        recipientUid: String,
        plaintext: ByteArray
    ): ByteArray {
        var recipientXPub: String? = xPubCache[recipientEdPubHex]

        if (recipientXPub == null) {
            val db = database
            if (db != null) {
                try {
                    val contact = db.contacts().get(recipientEdPubHex)
                    recipientXPub = contact?.xPub?.ifEmpty { null }
                } catch (_: Exception) {}
            }
        }

        if (recipientXPub == null && recipientUid.isNotEmpty()) {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(recipientUid)
                    .get()
                    .await()
                recipientXPub = doc.getString("xPub")
            } catch (_: Exception) {}
        }

        if (recipientXPub != null) {
            xPubCache[recipientEdPubHex] = recipientXPub!!
            return try {
                val envelope = E2ECrypto.encryptFor(identity, recipientXPub, plaintext)
                envelope.toByteArray(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Encryption failed, sending plaintext: ${e.message}")
                plaintext
            }
        }
        Log.w(TAG, "No recipient xPub available, sending plaintext")
        return plaintext
    }

    private suspend fun handleIncoming(
        senderEdPubHex: String,
        payload: ByteArray,
        senderName: String?,
        senderEmail: String?,
        db: SquelchDatabase,
        incomingMsgId: String? = null
    ) {
        if (senderEdPubHex == selfEdPubHex) return

        val blockedPubkeys = try { db.blocked().allPubkeys() } catch (_: Exception) { emptyList() }
        if (senderEdPubHex in blockedPubkeys) {
            Log.d(TAG, "Ignoring blocked sender $senderEdPubHex")
            try {
                val contact = db.contacts().get(senderEdPubHex)
                val recipientUid = contact?.firebaseUid ?: ""
                if (recipientUid.isNotEmpty()) {
                    val cmd = JSONObject().apply { put("cmd", "blocked") }
                    sendCommand(
                        recipientEdPubHex = senderEdPubHex,
                        recipientUid = recipientUid,
                        senderName = "",
                        kind = Transport.TransportFrame.KIND_BLOCKED,
                        payloadBytes = cmd.toString().toByteArray(Charsets.UTF_8)
                    )
                }
            } catch (_: Exception) {}
            return
        }

        val rawPayload = String(payload, Charsets.UTF_8)

        if (rawPayload.contains("\"hs\":") && rawPayload.contains("\"s\":") && rawPayload.contains("\"r\":")) {
            Log.d(TAG, "Dropping Noise handshake message from $senderEdPubHex")
            return
        }

        val plaintext: String
        if (rawPayload.contains("\"ct\":") && rawPayload.contains("\"sp\":")) {
            val identity = selfIdentity
            if (identity == null) {
                Log.w(TAG, "No identity, dropping encrypted message from $senderEdPubHex")
                return
            }
            val result = E2ECrypto.decryptWithMyKey(identity.xSecret, rawPayload)
            if (result == null) {
                Log.w(TAG, "Decryption failed, dropping encrypted message from $senderEdPubHex")
                return
            }
            plaintext = String(result.second, Charsets.UTF_8)
            Log.d(TAG, "Decrypted message from $senderEdPubHex")
        } else {
            plaintext = rawPayload
        }

        try {
            val json = JSONObject(plaintext)
            val command = json.optString("cmd", "")

            if (command == "recall") {
                val targetMsgId = json.optString("msgId", "")
                if (targetMsgId.isNotEmpty()) {
                    db.messages().delete(targetMsgId)
                    Log.d(TAG, "Recalled message $targetMsgId from $senderEdPubHex")
                }
                return
            }

            if (command == "edit") {
                val targetMsgId = json.optString("msgId", "")
                val newText = json.optString("text", "")
                if (targetMsgId.isNotEmpty() && newText.isNotEmpty()) {
                    db.messages().updateBody(targetMsgId, newText)
                    Log.d(TAG, "Edited message $targetMsgId from $senderEdPubHex")
                }
                return
            }

            if (command == "ack") {
                val targetMsgId = json.optString("msgId", "")
                if (targetMsgId.isNotEmpty()) {
                    db.messages().updateDelivery(targetMsgId, 2)
                    Log.d(TAG, "Delivery ack for $targetMsgId from $senderEdPubHex")
                }
                return
            }

            if (command == "read") {
                val targetMsgId = json.optString("msgId", "")
                if (targetMsgId.isNotEmpty()) {
                    db.messages().updateDelivery(targetMsgId, 3)
                    Log.d(TAG, "Read receipt for $targetMsgId from $senderEdPubHex")
                }
                return
            }

            if (command == "blocked") {
                Log.d(TAG, "Received block notification from $senderEdPubHex")
                blockEvents.trySend(BlockEvent(senderEdPubHex, blocked = true))
                return
            }

            if (command == "unblocked") {
                Log.d(TAG, "Received unblock notification from $senderEdPubHex")
                blockEvents.trySend(BlockEvent(senderEdPubHex, blocked = false))
                return
            }
        } catch (_: Exception) {
        }

        val conversationId = senderEdPubHex
        val contact = db.contacts().get(senderEdPubHex)
        val resolvedName = contact?.userId?.ifEmpty { null }
            ?: contact?.callsign?.ifEmpty { null }
            ?: contact?.displayName?.ifEmpty { null }
            ?: senderName?.ifEmpty { null }
            ?: senderEmail?.substringBefore("@")?.ifEmpty { null }
            ?: senderEdPubHex.take(8)

        Log.d(TAG, "Storing from $resolvedName: ${plaintext.take(50)}")

        val message = MessageEntity(
            conversationId = conversationId,
            msgId = incomingMsgId ?: UUID.randomUUID().toString(),
            sender = senderEdPubHex,
            body = plaintext,
            timestamp = System.currentTimeMillis(),
            direction = 0,
            delivery = 1,
            kind = 2
        )
        db.messages().insert(message)

        val existingConv = db.conversations().get(conversationId)
        if (existingConv == null) {
            db.conversations().upsert(
                ConversationEntity(
                    id = conversationId,
                    name = resolvedName,
                    lastMessagePreview = plaintext.take(80),
                    lastMessageTimestamp = message.timestamp,
                    unreadCount = 1
                )
            )
        } else {
            db.conversations().updateLastMessage(
                id = conversationId,
                preview = plaintext.take(80),
                timestamp = message.timestamp
            )
            db.conversations().incrementUnread(conversationId)
        }
        Log.d(TAG, "Stored message from $resolvedName")

        try {
            val ackCmd = JSONObject().apply {
                put("cmd", "ack")
                put("msgId", message.msgId)
            }
            val contact = db.contacts().get(senderEdPubHex)
            val senderUid = contact?.firebaseUid ?: ""
            if (senderUid.isNotEmpty()) {
                sendCommand(
                    recipientEdPubHex = senderEdPubHex,
                    recipientUid = senderUid,
                    senderName = "",
                    kind = Transport.TransportFrame.KIND_DATA,
                    payloadBytes = ackCmd.toString().toByteArray(Charsets.UTF_8)
                )
            }
        } catch (_: Exception) {}

        try {
            Notifications.showMessageNotification(
                context = context,
                notificationId = conversationId.hashCode(),
                title = resolvedName,
                body = plaintext.take(100),
                conversationId = conversationId
            )
            Log.d(TAG, "Notification shown for message from $resolvedName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification: ${e.message}")
        }
    }

    private suspend fun cleanupOldMessages() {
        try {
            val fireDb = FirebaseFirestore.getInstance()
            val cutoffMs = System.currentTimeMillis() - MSG_TTL_HOURS * 60 * 60 * 1000L
            val cutoffDate = java.util.Date(cutoffMs)
            val cutoffTimestamp = com.google.firebase.Timestamp(cutoffDate)

            val oldMsgs = fireDb.collection("messages")
                .whereLessThan("timestamp", cutoffTimestamp)
                .get()
                .await()
            var deletedCount = 0
            for (doc in oldMsgs.documents) {
                try { doc.reference.delete().await() ; deletedCount++ } catch (_: Exception) {}
            }
            Log.d(TAG, "TTL cleanup: deleted $deletedCount old messages")

            val oldAcks = fireDb.collection("delivery_acks")
                .whereLessThan("timestamp", cutoffTimestamp)
                .get()
                .await()
            var ackDeleted = 0
            for (doc in oldAcks.documents) {
                try { doc.reference.delete().await() ; ackDeleted++ } catch (_: Exception) {}
            }
            Log.d(TAG, "TTL cleanup: deleted $ackDeleted old delivery acks")
        } catch (e: Exception) {
            Log.e(TAG, "TTL cleanup failed: ${e.message}")
        }
    }
}
