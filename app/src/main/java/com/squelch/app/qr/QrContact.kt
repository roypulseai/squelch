package com.squelch.app.qr

data class QrContact(
    val edPub: String,
    val xPub: String,
    val callsign: String,
    val displayName: String = "",
    val userId: String = ""
)
