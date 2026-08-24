package com.squelch.app.mesh.engine

import android.content.Context
import android.util.Log
import com.squelch.app.auth.AuthRepository
import com.squelch.app.crypto.Identity
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.mesh.transport.BleTransport
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.util.Notifications
import com.squelch.app.util.toHex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshEngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val vaultRepository: VaultRepository
) {
    companion object {
        private const val TAG = "MeshEngineManager"
    }

    @Volatile
    private var engine: MeshEngine? = null
    private var scope: CoroutineScope? = null
    private val _typingEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 16)
    val typingEvents: kotlinx.coroutines.flow.SharedFlow<String> = _typingEvents.asSharedFlow()

    @Synchronized
    fun getOrCreate(): MeshEngine? {
        engine?.let { if (it.running) return it }
        return try {
            val googleUid = authRepository.signedIn()?.googleUid ?: return null
            val identity = Identity.fromGoogleUid(googleUid)
            val edPubHex = identity.edPub.toHex()

            val transports = mutableListOf<Transport>()
            transports.add(BleTransport(context, edPubHex))

            val eng = MeshEngine(identity = identity, transports = transports)
            eng.start()
            engine = eng

            val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = s
            s.launch { collectMeshMessages(eng) }

            Log.d(TAG, "MeshEngine created and started")
            eng
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MeshEngine: ${e.message}", e)
            null
        }
    }

    fun get(): MeshEngine? = engine

    private suspend fun collectMeshMessages(eng: MeshEngine) {
        eng.messages.collect { incoming ->
            try {
                val db = vaultRepository.db ?: return@collect
                if (incoming.senderEdPubHex == eng.selfPubHex) return@collect

                val plaintext = String(incoming.plaintext, Charsets.UTF_8)

                // Handle typing indicator
                if (plaintext.startsWith("{\"typing\":")) {
                    _typingEvents.emit(incoming.senderEdPubHex)
                    return@collect
                }

                val contact = db.contacts().get(incoming.senderEdPubHex)
                val senderName = contact?.userId?.ifEmpty { null }
                    ?: contact?.displayName?.ifEmpty { null }
                    ?: contact?.callsign?.ifEmpty { null }
                    ?: incoming.senderEdPubHex.take(8)
                val conversationId = incoming.senderEdPubHex

                val msgId = UUID.randomUUID().toString()
                val message = MessageEntity(
                    conversationId = conversationId,
                    msgId = msgId,
                    sender = incoming.senderEdPubHex,
                    body = plaintext,
                    timestamp = incoming.timestamp,
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
                            lastMessageTimestamp = incoming.timestamp,
                            unreadCount = 1
                        )
                    )
                } else {
                    db.conversations().updateLastMessage(
                        id = conversationId,
                        preview = plaintext.take(80),
                        timestamp = incoming.timestamp
                    )
                    db.conversations().incrementUnread(conversationId)
                }

                Notifications.showMessageNotification(
                    context = context,
                    notificationId = conversationId.hashCode(),
                    title = senderName,
                    body = plaintext.take(100),
                    conversationId = conversationId
                )

                Log.d(TAG, "Mesh message from $senderName: ${plaintext.take(50)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle mesh message: ${e.message}")
            }
        }
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
        engine?.stop()
        engine = null
        Log.d(TAG, "MeshEngine stopped")
    }
}
