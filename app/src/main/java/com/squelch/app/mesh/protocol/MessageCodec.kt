package com.squelch.app.mesh.protocol

import org.json.JSONObject
import java.util.Base64

object MessageCodec {

    data class MeshMessage(
        val sender: String,
        val recipient: String,
        val msgId: String,
        val ciphertext: ByteArray,
        val timestamp: Long,
        val ttl: Int = 7,
        val hopCount: Int = 0,
        val originalSender: String = sender
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MeshMessage) return false
            return msgId == other.msgId
        }
        override fun hashCode(): Int = msgId.hashCode()
    }

    fun encode(msg: MeshMessage): ByteArray {
        val json = JSONObject().apply {
            put("s", msg.sender)
            put("r", msg.recipient)
            put("id", msg.msgId)
            put("ct", Base64.getEncoder().encodeToString(msg.ciphertext))
            put("ts", msg.timestamp)
            put("ttl", msg.ttl)
            put("hc", msg.hopCount)
            if (msg.originalSender != msg.sender) {
                put("os", msg.originalSender)
            }
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(data: ByteArray): MeshMessage? {
        return try {
            val json = JSONObject(String(data, Charsets.UTF_8))
            MeshMessage(
                sender = json.getString("s"),
                recipient = json.getString("r"),
                msgId = json.getString("id"),
                ciphertext = Base64.getDecoder().decode(json.getString("ct")),
                timestamp = json.getLong("ts"),
                ttl = json.optInt("ttl", 7),
                hopCount = json.optInt("hc", 0),
                originalSender = json.optString("os", json.getString("s"))
            )
        } catch (e: Exception) {
            null
        }
    }
}
