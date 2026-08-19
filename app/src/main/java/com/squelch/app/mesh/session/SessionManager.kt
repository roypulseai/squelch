package com.squelch.app.mesh.session

import android.util.Log
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.mesh.engine.MeshEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class SessionManager(
    private val engine: MeshEngine,
    private val database: SquelchDatabase?,
    private val selfPubkey: String
) {
    companion object {
        private const val TAG = "SessionManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        engine.start()
        job = scope.launch {
            engine.messages.collect { incoming ->
                handleIncomingMessage(incoming)
            }
        }
        Log.d(TAG, "SessionManager started")
    }

    fun stop() {
        job?.cancel()
        engine.stop()
        Log.d(TAG, "SessionManager stopped")
    }

    private suspend fun handleIncomingMessage(msg: MeshEngine.IncomingMessage) {
        val db = database ?: return
        if (msg.senderEdPubHex == selfPubkey) return

        val contact = db.contacts().get(msg.senderEdPubHex)
        val senderName = contact?.callsign ?: contact?.displayName ?: msg.senderEdPubHex.take(8)
        val conversationId = msg.senderEdPubHex
        val plaintext = String(msg.plaintext, Charsets.UTF_8)

        val message = MessageEntity(
            conversationId = conversationId,
            msgId = UUID.randomUUID().toString(),
            sender = msg.senderEdPubHex,
            body = plaintext,
            timestamp = msg.timestamp,
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
                    lastMessageTimestamp = msg.timestamp,
                    unreadCount = 1
                )
            )
        } else {
            db.conversations().updateLastMessage(
                id = conversationId,
                preview = plaintext.take(80),
                timestamp = msg.timestamp
            )
            db.conversations().incrementUnread(conversationId)
        }

        Log.d(TAG, "Received message from $senderName: ${plaintext.take(50)}")
    }
}
