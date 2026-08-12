package com.squelch.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A peer we've made contact with, keyed by Ed25519 pubkey (hex). */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey @ColumnInfo(name = "pubkey") val pubkey: String,
    val xPub: String,
    val callsign: String,
    val trustLevel: Int,                 // TrustLevel.MET=0, VERIFIED=1, RELAYED=2
    val capabilities: Int,
    val lastSeen: Long,
    val bluetoothAddress: String,
    val mutualStatics: Boolean,
    val addedAt: Long
)

/** A chat room / broadcast channel (squelch's M3+ "Room" concept).
 *  We name it `chat_rooms` in the schema to avoid colliding with Room
 *  the ORM library if anyone ever has to read both in the same file. */
@Entity(tableName = "chat_rooms")
data class ChatRoomEntity(
    @PrimaryKey val id: String,         // hex hash of (name + passphrase)
    val name: String,
    val passphrase: String,
    val joined: Boolean,
    val createdAt: Long,
    val lastReadAt: Long                 // for unread counters (M7)
)

/** A single message in a DM or chat room. */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,         // peer pubkey hex for DM, chat room id for room
    val msgId: String,                   // hex of the inner / outer MsgID
    val sender: String,                  // ed pubkey hex, or "me"
    val body: String,
    val timestamp: Long,
    val direction: Int,                  // 0 in, 1 out
    val delivery: Int,                   // 0 sending, 1 sent, 2 queued, 3 delivered
    val kind: Int                        // 0 chat, 1 room, 2 ack, 3 game
)

/** Singleton-ish settings blob. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
