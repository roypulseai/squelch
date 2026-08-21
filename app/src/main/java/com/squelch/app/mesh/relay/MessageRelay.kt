package com.squelch.app.mesh.relay

import android.content.Context
import android.util.Log
import com.squelch.app.crypto.Identity
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.messaging.MessageRelayHolder
import com.squelch.app.mesh.transport.FirestoreTransport
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.util.Notifications
import com.squelch.app.util.toHex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
    }

    data class BlockEvent(val peerEdPubHex: String, val blocked: Boolean)

    val blockEvents = Channel<BlockEvent>(Channel.BUFFERED)

    private var transport: FirestoreTransport? = null
    private var scope: CoroutineScope? = null
    private var incomingJob: kotlinx.coroutines.Job? = null

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

    fun start(edPubHex: String, database: SquelchDatabase, identity: Identity, email: String = "") {
        if (transport != null) {
            Log.d(TAG, "Already running for $edPubHex")
            return
        }
        selfEdPubHex = edPubHex
        selfIdentity = identity
        selfEmail = email
        MessageRelayHolder.database = database
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
                        handleIncoming(frame.senderEdPubHex, frame.payload, frame.senderName, frame.senderEmail, database, frame.msgId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Handle incoming failed: ${e.message}", e)
                    }
                }
            }
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
            t.sendWithMeta(
                recipientEdPubHex = recipientEdPubHex,
                recipientUid = recipientUid,
                senderName = senderName,
                senderEmail = selfEmail,
                payload = plaintext.toByteArray(Charsets.UTF_8),
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
            t.sendWithMeta(
                recipientEdPubHex = recipientEdPubHex,
                recipientUid = recipientUid,
                senderName = senderName,
                senderEmail = selfEmail,
                payload = payloadBytes,
                kind = kind
            )
            Log.d(TAG, "Command sent (kind=$kind) to $recipientEdPubHex")
        }
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
                    sendCommand(
                        recipientEdPubHex = senderEdPubHex,
                        recipientUid = recipientUid,
                        senderName = "",
                        kind = Transport.TransportFrame.KIND_BLOCKED,
                        payloadBytes = "blocked".toByteArray(Charsets.UTF_8)
                    )
                }
            } catch (_: Exception) {}
            return
        }

        val plaintext = String(payload, Charsets.UTF_8)

        if (plaintext.contains("\"hs\":") && plaintext.contains("\"s\":") && plaintext.contains("\"r\":")) {
            Log.d(TAG, "Dropping Noise handshake message from $senderEdPubHex")
            return
        }
        if (plaintext.contains("\"ct\":") && plaintext.contains("\"s\":") && plaintext.contains("\"r\":")) {
            Log.d(TAG, "Dropping Noise mesh message from $senderEdPubHex")
            return
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
        val resolvedName = contact?.callsign?.ifEmpty { null }
            ?: contact?.displayName?.ifEmpty { null }
            ?: senderName?.ifEmpty { null }
            ?: senderEmail?.ifEmpty { null }
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
}
