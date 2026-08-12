package com.squelch.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A known peer (spec 3 trust levels; keyed by Ed25519 pubkey hex). */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val pubkey: String,          // ed25519 pubkey hex
    val xPub: String,                        // x25519 static pubkey hex
    val callsign: String,
    val trustLevel: Int,                     // 0 met, 1 verified, 2 relayed
    val capabilities: Int,
    val lastSeen: Long,
    val bluetoothAddress: String,
    val mutualStatics: Boolean
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,              // peer pubkey hex for DMs, roomId for rooms
    val kind: Int,                           // 0 DM, 1 room
    val title: String,
    val updatedAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val msgId: String,                       // hex
    val sender: String,                      // ed pubkey hex, or "me"
    val body: String,
    val timestamp: Long,
    val direction: Int,                      // 0 in, 1 out
    val delivery: Int,                       // 0 sending, 1 sent, 2 queued, 3 delivered
    val kind: Int                            // 0 chat, 1 room, 2 ack (internal), 3 game
)

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,              // roomId hex (hash of name/passphrase)
    val name: String,
    val passphrase: String,
    val joined: Boolean,
    val createdAt: Long
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
