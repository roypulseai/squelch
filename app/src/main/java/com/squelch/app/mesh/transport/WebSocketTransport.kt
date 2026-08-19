package com.squelch.app.mesh.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class WebSocketTransport(
    private val relayUrl: String,
    private val authToken: String,
    private val edPubHex: String
) : Transport {

    companion object {
        private const val TAG = "WsTransport"
    }

    override val name: String = "WebSocket"

    private val _incoming = MutableSharedFlow<Transport.TransportFrame>(extraBufferCapacity = 16)
    override val incoming: SharedFlow<Transport.TransportFrame> = _incoming.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    override fun start() {
        val request = Request.Builder()
            .url(relayUrl)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("X-Squelch-EdPub", edPubHex)
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to relay")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleText(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                reconnect()
            }
        })
    }

    override fun stop() {
        job?.cancel()
        ws?.close(1000, "stopped")
        ws = null
    }

    override fun send(recipientEdPubHex: String, payload: ByteArray) {
        val frame = JSONObject().apply {
            put("t", "m")
            put("f", edPubHex)
            put("p", Base64.getEncoder().encodeToString(payload))
        }
        ws?.send(frame.toString())
    }

    private fun handleText(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("t", "")

            when (type) {
                "hi" -> {
                    val peers = json.optJSONArray("peers")
                    if (peers != null) {
                        for (i in 0 until peers.length()) {
                            val peerHex = peers.getString(i)
                            if (peerHex != edPubHex) {
                                scope.launch {
                                    _incoming.emit(
                                        Transport.TransportFrame(
                                            senderEdPubHex = peerHex,
                                            kind = Transport.TransportFrame.KIND_HELLO,
                                            payload = ByteArray(0)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                "m" -> {
                    val sender = json.optString("f", "")
                    val payloadB64 = json.optString("p", "")
                    val kind = json.optInt("k", Transport.TransportFrame.KIND_DATA)
                    if (sender.isNotEmpty() && payloadB64.isNotEmpty()) {
                        scope.launch {
                            _incoming.emit(
                                Transport.TransportFrame(
                                    senderEdPubHex = sender,
                                    kind = kind,
                                    payload = Base64.getDecoder().decode(payloadB64)
                                )
                            )
                        }
                    }
                }
                "bye" -> {
                    val peer = json.optString("f", "")
                    Log.d(TAG, "Peer left: $peer")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse frame: ${e.message}")
        }
    }

    private fun reconnect() {
        job?.cancel()
        job = scope.launch {
            kotlinx.coroutines.delay(5000)
            Log.d(TAG, "Reconnecting...")
            start()
        }
    }
}
