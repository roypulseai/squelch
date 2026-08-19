package com.squelch.app.crypto

import org.json.JSONArray
import org.json.JSONObject

data class VaultPayload(
    val version: Int = 1,
    val mnemonic: String,
    val contacts: List<ContactEntry> = emptyList(),
    val settings: Settings = Settings()
) {
    data class ContactEntry(
        val edPub: String,
        val xPub: String,
        val callsign: String,
        val trustLevel: Int,
        val lastSeen: Long
    )

    data class Settings(
        val theme: String = "default",
        val showCallsigns: Boolean = true,
        val meshPublic: Boolean = true
    )

    fun toJsonString(): String {
        val contactsJson = JSONArray()
        for (c in contacts) {
            contactsJson.put(
                JSONObject()
                    .put("edPub", c.edPub)
                    .put("xPub", c.xPub)
                    .put("callsign", c.callsign)
                    .put("trustLevel", c.trustLevel)
                    .put("lastSeen", c.lastSeen)
            )
        }
        val settingsJson = JSONObject()
            .put("theme", settings.theme)
            .put("showCallsigns", settings.showCallsigns)
            .put("meshPublic", settings.meshPublic)
        val root = JSONObject()
            .put("version", version)
            .put("mnemonic", mnemonic)
            .put("contacts", contactsJson)
            .put("settings", settingsJson)
        return root.toString()
    }

    companion object {
        fun fromJsonString(json: String): VaultPayload {
            val root = JSONObject(json)
            val contactsArr = root.optJSONArray("contacts") ?: JSONArray()
            val contacts = buildList {
                for (i in 0 until contactsArr.length()) {
                    val c = contactsArr.getJSONObject(i)
                    add(
                        ContactEntry(
                            edPub = c.optString("edPub"),
                            xPub = c.optString("xPub"),
                            callsign = c.optString("callsign"),
                            trustLevel = c.optInt("trustLevel", 0),
                            lastSeen = c.optLong("lastSeen", 0L)
                        )
                    )
                }
            }
            val sObj = root.optJSONObject("settings")
            val settings = if (sObj != null) Settings(
                theme = sObj.optString("theme", "default"),
                showCallsigns = sObj.optBoolean("showCallsigns", true),
                meshPublic = sObj.optBoolean("meshPublic", true)
            ) else Settings()
            return VaultPayload(
                version = root.optInt("version", 1),
                mnemonic = root.getString("mnemonic"),
                contacts = contacts,
                settings = settings
            )
        }
    }
}
