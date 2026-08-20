package com.squelch.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessagePreview: String = "",
    val lastMessageTimestamp: Long = 0,
    val unreadCount: Int = 0,
    val muted: Boolean = false
)
