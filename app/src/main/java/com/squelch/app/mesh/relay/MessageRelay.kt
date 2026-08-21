package com.squelch.app.mesh.relay

import android.util.Log
import com.squelch.app.crypto.Identity
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.messaging.MessageRelayHolder
import com.squelch.app.mesh.transport.FirestoreTransport
import com.squelch.app.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRelay @Inject constructor() {

    companion object {
        private const val TAG = "MessageRelay"
    }

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
                        handleIncoming(frame.senderEdPubHex, frame.payload, frame.senderName, frame.senderEmail, database)
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
        plaintext: String
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
                payload = plaintext.toByteArray(Charsets.UTF_8)
            )
            Log.d(TAG, "Message sent to $recipientEdPubHex")
        }
    }

    private suspend fun handleIncoming(
        senderEdPubHex: String,
        payload: ByteArray,
        senderName: String?,
        senderEmail: String?,
        db: SquelchDatabase
    ) {
        if (senderEdPubHex == selfEdPubHex) return

        val blockedPubkeys = try { db.blocked().allPubkeys() } catch (_: Exception) { emptyList() }
        if (senderEdPubHex in blockedPubkeys) {
            Log.d(TAG, "Ignoring blocked sender $senderEdPubHex")
            return
        }

        val plaintext = String(payload, Charsets.UTF_8)
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
            msgId = UUID.randomUUID().toString(),
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
    }
}
