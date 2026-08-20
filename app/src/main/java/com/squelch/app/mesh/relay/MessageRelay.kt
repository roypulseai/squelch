package com.squelch.app.mesh.relay

import android.util.Log
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.mesh.transport.FirestoreTransport
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

    val isRunning: Boolean get() = transport != null

    fun start(edPubHex: String, database: SquelchDatabase) {
        if (transport != null) {
            Log.d(TAG, "Already running")
            return
        }
        val t = FirestoreTransport(edPubHex)
        transport = t
        t.start()

        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        incomingJob = s.launch {
            t.incoming.collect { frame ->
                try {
                    handleIncoming(edPubHex, frame.senderEdPubHex, frame.payload, database)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle incoming: ${e.message}")
                }
            }
        }
        Log.d(TAG, "MessageRelay started for $edPubHex")
    }

    fun stop() {
        incomingJob?.cancel()
        incomingJob = null
        transport?.stop()
        transport = null
        scope?.cancel()
        scope = null
        Log.d(TAG, "MessageRelay stopped")
    }

    fun send(recipientEdPubHex: String, plaintext: String, senderEdPubHex: String) {
        val t = transport ?: run {
            Log.e(TAG, "Transport not running, cannot send")
            return
        }
        t.send(recipientEdPubHex, plaintext.toByteArray(Charsets.UTF_8))
        Log.d(TAG, "Sent message to $recipientEdPubHex")
    }

    private suspend fun handleIncoming(
        selfEdPubHex: String,
        senderEdPubHex: String,
        payload: ByteArray,
        db: SquelchDatabase
    ) {
        if (senderEdPubHex == selfEdPubHex) return

        val plaintext = String(payload, Charsets.UTF_8)
        val conversationId = senderEdPubHex
        val contact = db.contacts().get(senderEdPubHex)
        val senderName = contact?.callsign?.ifEmpty { null }
            ?: contact?.displayName?.ifEmpty { null }
            ?: senderEdPubHex.take(8)

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
                    name = senderName,
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
        Log.d(TAG, "Received from $senderName: ${plaintext.take(50)}")
    }
}
