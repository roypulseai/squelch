package com.squelch.app.mesh.engine

import android.util.Log
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.noise.HandshakePattern
import com.squelch.app.crypto.noise.NoiseSession
import com.squelch.app.mesh.protocol.MessageCodec
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.util.Bytes
import com.squelch.app.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID

class MeshEngine(
    private val identity: Identity,
    private val transports: List<Transport>
) {
    companion object {
        private const val TAG = "MeshEngine"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val sessions = mutableMapOf<String, NoiseSession>()
    private val rng = SecureRandom()

    private val _peers = MutableStateFlow<Set<String>>(emptySet())
    val peers: StateFlow<Set<String>> = _peers.asStateFlow()

    private val _messages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<IncomingMessage> = _messages.asSharedFlow()

    data class IncomingMessage(
        val senderEdPubHex: String,
        val plaintext: ByteArray,
        val timestamp: Long
    )

    fun start() {
        for (transport in transports) {
            transport.start()
            scope.launch {
                transport.incoming.collect { frame ->
                    handleFrame(frame)
                }
            }
        }
    }

    fun stop() {
        for (transport in transports) transport.stop()
        sessions.clear()
    }

    fun sendMessage(recipientEdPubHex: String, plaintext: ByteArray) {
        val session = sessions[recipientEdPubHex]
        val ciphertext: ByteArray
        val kind: Int
        if (session != null && session.isHandshakeComplete) {
            ciphertext = session.encrypt(plaintext)
            kind = Transport.TransportFrame.KIND_DATA
        } else {
            ciphertext = plaintext
            kind = Transport.TransportFrame.KIND_HS
        }
        val msg = MessageCodec.MeshMessage(
            sender = identity.edPub.toHex(),
            recipient = recipientEdPubHex,
            msgId = UUID.randomUUID().toString(),
            ciphertext = ciphertext,
            timestamp = System.currentTimeMillis()
        )
        val encoded = MessageCodec.encode(msg)
        for (transport in transports) {
            transport.send(recipientEdPubHex, encoded)
        }
    }

    private fun handleFrame(frame: Transport.TransportFrame) {
        val sender = frame.senderEdPubHex
        if (sender == identity.edPub.toHex()) return

        _peers.value = _peers.value + sender

        when (frame.kind) {
            Transport.TransportFrame.KIND_HELLO -> {
                Log.d(TAG, "HELLO from $sender")
                initiateHandshake(sender)
            }
            Transport.TransportFrame.KIND_HS -> {
                handleHandshake(sender, frame.payload)
            }
            Transport.TransportFrame.KIND_DATA -> {
                handleData(sender, frame.payload)
            }
        }
    }

    private fun initiateHandshake(remotePubHex: String) {
        val remotePub = Bytes.unhex(remotePubHex)
        val session = NoiseSession.createInitiator(
            pattern = HandshakePattern.XX,
            localStatic = identity.edKeyPair(),
            remoteStatic = remotePub,
            prologue = ByteArray(0)
        )
        sessions[remotePubHex] = session
        val hsMsg = session.writeHandshake()
        val wrapped = wrapHandshakeMessage(remotePubHex, hsMsg)
        for (transport in transports) {
            transport.send(remotePubHex, wrapped)
        }
    }

    private fun handleHandshake(sender: String, payload: ByteArray) {
        val unwrapped = unwrapHandshakeMessage(payload) ?: return
        val session = sessions.getOrPut(sender) {
            val remotePub = Bytes.unhex(sender)
            NoiseSession.createResponder(
                pattern = HandshakePattern.XX,
                localStatic = identity.edKeyPair(),
                remoteStatic = remotePub,
                prologue = ByteArray(0)
            )
        }
        if (session.isHandshakeComplete) return
        try {
            session.readHandshake(unwrapped)
            if (!session.isHandshakeComplete) {
                val response = session.writeHandshake()
                val wrapped = wrapHandshakeMessage(sender, response)
                for (transport in transports) {
                    transport.send(sender, wrapped)
                }
            }
            Log.d(TAG, "Handshake complete with $sender")
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed with $sender: ${e.message}")
            sessions.remove(sender)
        }
    }

    private fun handleData(sender: String, payload: ByteArray) {
        val session = sessions[sender]
        if (session == null || !session.isHandshakeComplete) {
            Log.w(TAG, "No session for $sender, initiating handshake")
            initiateHandshake(sender)
            return
        }
        try {
            val plaintext = session.decrypt(payload)
            val msg = MessageCodec.decode(plaintext) ?: return
            if (msg.recipient != identity.edPub.toHex()) return
            scope.launch {
                _messages.emit(
                    IncomingMessage(
                        senderEdPubHex = msg.sender,
                        plaintext = msg.ciphertext,
                        timestamp = msg.timestamp
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed from $sender: ${e.message}")
        }
    }

    private fun wrapHandshakeMessage(remotePubHex: String, hsMsg: ByteArray): ByteArray {
        val json = org.json.JSONObject().apply {
            put("s", identity.edPub.toHex())
            put("r", remotePubHex)
            put("hs", java.util.Base64.getEncoder().encodeToString(hsMsg))
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun unwrapHandshakeMessage(data: ByteArray): ByteArray? {
        return try {
            val json = org.json.JSONObject(String(data, Charsets.UTF_8))
            val hs = json.optString("hs", "")
            if (hs.isNotEmpty()) java.util.Base64.getDecoder().decode(hs) else null
        } catch (e: Exception) {
            null
        }
    }
}
