package com.squelch.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey @ColumnInfo(name = "pubkey") val pubkey: String,
    val xPub: String,
    val callsign: String,
    val displayName: String = "",
    val trustLevel: Int = 0,
    val capabilities: Int = 0,
    val lastSeen: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)
