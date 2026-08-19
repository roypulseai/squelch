package com.squelch.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val msgId: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val direction: Int = 0,
    val delivery: Int = 0,
    val kind: Int = 0,
    val readAt: Long? = null
)
