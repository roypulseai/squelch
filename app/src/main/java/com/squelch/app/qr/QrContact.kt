package com.squelch.app.qr

import android.net.Uri

/**
 * Squelch contact wire format for QR-code exchange.
 *
 *   squelch://contact?ed=<edPubHex>&xp=<xPubHex>&c=<callsign>&t=<trust>&v=1
 *
 * Designed so a single Android / iOS contact QR can be scanned by the
 * other platform's camera and added with one tap. v=1 is the format
 * version; future versions can branch off.
 */
object QrContact {

    const val SCHEME = "squelch"
    const val HOST = "contact"
    const val VERSION = "1"

    data class Contact(
        val edPubHex: String,
        val xPubHex: String,
        val callsign: String,
        val trustLevel: Int
    )

    fun encode(c: Contact): String {
        val uri = Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("ed", c.edPubHex)
            .appendQueryParameter("xp", c.xPubHex)
            .appendQueryParameter("c", c.callsign)
            .appendQueryParameter("t", c.trustLevel.toString())
            .appendQueryParameter("v", VERSION)
            .build()
        return uri.toString()
    }

    fun decode(text: String): Contact? {
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME) return null
        if (uri.host != HOST) return null
        val ed = uri.getQueryParameter("ed") ?: return null
        val xp = uri.getQueryParameter("xp") ?: return null
        val callsign = uri.getQueryParameter("c") ?: ""
        val trust = uri.getQueryParameter("t")?.toIntOrNull() ?: 0
        return Contact(
            edPubHex = ed,
            xPubHex = xp,
            callsign = callsign,
            trustLevel = trust
        )
    }
}