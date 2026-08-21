package com.squelch.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val pubkey: String,
    val firebaseUid: String = "",
    val xPub: String = "",
    val callsign: String = "",
    val displayName: String = "",
    val lastSeen: Long = 0,
    val email: String = ""
)
