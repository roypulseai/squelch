package com.squelch.app.mesh

import com.squelch.app.db.AppDatabase
import com.squelch.app.db.ConversationEntity
import com.squelch.app.db.MessageEntity
import com.squelch.app.util.Bytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.SecureRandom

object DeliveryState {
    const val SENDING = 0
    const val SENT = 1
    const val QUEUED = 2
    const val DELIVERED = 3

    fun glyph(state: Int): String = when (state) {
        SENT -> ">"
        QUEUED -> "~"
        DELIVERED -> "<"
        else -> "?"
    }
}

/**
 * Conversation state, delivery receipts and local persistence (spec 6).
 * Messages live on-device only; purge policy handled by settings.
 */
class MessageLayer(
    private val engine: MeshEngine,
    private val db: AppDatabase
) {
    private val random = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendDm(peer: Peer, text: String) {
        val msgId = Bytes.randomId(random)
        val now = System.currentTimeMillis()
        scope.launch {
            db.messages().insert(
                MessageEntity(
                    conversationId = peer.edHex,
                    msgId = Bytes.hex(msgId),
                    sender = "me",
                    body = text,
                    timestamp = now,
                    direction = 1,
                    delivery = DeliveryState.SENDING,
                    kind = 0
                )
            )
            db.conversations().upsert(
                ConversationEntity(peer.edHex, 0, engine.peers.displayCallsign(peer.edHex), now)
            )
        }
        engine.sessionManager.queueForPeer(peer, InnerMessage.chat(now, msgId, text))
    }

    fun sendRoomMessage(room: RoomManager.Room, text: String) {
        val msgId = Bytes.randomId(random)
        val now = System.currentTimeMillis()
        scope.launch {
            db.messages().insert(
                MessageEntity(
                    conversationId = room.idHex,
                    msgId = Bytes.hex(msgId),
                    sender = "me",
                    body = text,
                    timestamp = now,
                    direction = 1,
                    delivery = DeliveryState.DELIVERED,
                    kind = 1
                )
            )
            db.conversations().upsert(ConversationEntity(room.idHex, 1, room.name, now))
        }
        engine.sendRoom(room, InnerMessage.roomMsg(now, msgId, text).encode())
    }

    /** A DM addressed to me was decrypted by the session layer. */
    fun onDmReceived(senderPeer: Peer, inner: InnerMessage) {
        when (inner.kind) {
            InnerMessage.KIND_CHAT -> {
                scope.launch {
                    db.messages().insert(
                        MessageEntity(
                            conversationId = senderPeer.edHex,
                            msgId = Bytes.hex(inner.msgId),
                            sender = senderPeer.edHex,
                            body = inner.text,
                            timestamp = inner.timestamp,
                            direction = 0,
                            delivery = DeliveryState.DELIVERED,
                            kind = 0
                        )
                    )
                    db.conversations().upsert(
                        ConversationEntity(senderPeer.edHex, 0, engine.peers.displayCallsign(senderPeer.edHex), inner.timestamp)
                    )
                }
                ack(senderPeer, inner.msgId)
            }
            InnerMessage.KIND_ACK -> {
                scope.launch {
                    db.messages().getByMsgId(Bytes.hex(inner.msgId))?.let { msg ->
                        if (msg.direction == 1) {
                            db.messages().updateDelivery(Bytes.hex(inner.msgId), DeliveryState.DELIVERED)
                        }
                    }
                }
            }
        }
    }

    private fun ack(senderPeer: Peer, originalMsgId: ByteArray) {
        val ackInner = InnerMessage.ack(originalMsgId)
        engine.sessionManager.queueForPeer(senderPeer, ackInner)
    }

    fun onRoomReceived(room: RoomManager.Room, senderEd: ByteArray, inner: InnerMessage) {
        when (inner.kind) {
            InnerMessage.KIND_ROOM_MSG -> {
                scope.launch {
                    db.messages().insert(
                        MessageEntity(
                            conversationId = room.idHex,
                            msgId = Bytes.hex(inner.msgId),
                            sender = Bytes.hex(senderEd),
                            body = inner.text,
                            timestamp = inner.timestamp,
                            direction = 0,
                            delivery = DeliveryState.DELIVERED,
                            kind = 1
                        )
                    )
                    db.conversations().upsert(ConversationEntity(room.idHex, 1, room.name, inner.timestamp))
                }
            }
            else -> Unit
        }
    }

    fun markSent(msgId: ByteArray) {
        scope.launch {
            db.messages().updateDelivery(Bytes.hex(msgId), DeliveryState.SENT)
        }
    }

    fun markQueued(msgId: ByteArray) {
        scope.launch {
            db.messages().updateDelivery(Bytes.hex(msgId), DeliveryState.QUEUED)
        }
    }

    fun purge(before: Long) {
        scope.launch {
            db.messages().purgeBefore(before)
        }
    }
}
