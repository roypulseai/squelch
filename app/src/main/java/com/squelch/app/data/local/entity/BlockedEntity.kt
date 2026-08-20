package com.squelch.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked")
data class BlockedEntity(
    @PrimaryKey val edPubHex: String,
    val displayName: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)
