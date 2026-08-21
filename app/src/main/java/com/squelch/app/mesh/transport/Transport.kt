package com.squelch.app.mesh.transport

import kotlinx.coroutines.flow.Flow

interface Transport {
    val name: String
    fun start()
    fun stop()
    fun send(recipientEdPubHex: String, payload: ByteArray)
    val incoming: Flow<TransportFrame>

    data class TransportFrame(
        val senderEdPubHex: String,
        val kind: Int,
        val payload: ByteArray,
        val senderName: String? = null,
        val senderEmail: String? = null
    ) {
        companion object {
            const val KIND_HELLO = 1
            const val KIND_DATA = 2
            const val KIND_HS = 3
            const val KIND_RECALL = 4
            const val KIND_EDIT = 5
        }
    }
}
