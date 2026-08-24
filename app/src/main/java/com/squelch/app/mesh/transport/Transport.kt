package com.squelch.app.mesh.transport

import kotlinx.coroutines.flow.Flow

interface Transport {
    val name: String
    fun start()
    fun stop()
    fun send(recipientEdPubHex: String, payload: ByteArray, kind: Int = TransportFrame.KIND_DATA)
    val incoming: Flow<TransportFrame>

    data class TransportFrame(
        val senderEdPubHex: String,
        val kind: Int,
        val payload: ByteArray,
        val senderName: String? = null,
        val senderEmail: String? = null,
        val msgId: String? = null
    ) {
        companion object {
            const val KIND_HELLO = 1
            const val KIND_DATA = 2
            const val KIND_HS = 3
            const val KIND_RECALL = 4
            const val KIND_EDIT = 5
            const val KIND_BLOCKED = 6
            const val KIND_UNBLOCKED = 7
            const val KIND_TYPING = 8
            const val KIND_PRESENCE = 9
        }
    }
}
