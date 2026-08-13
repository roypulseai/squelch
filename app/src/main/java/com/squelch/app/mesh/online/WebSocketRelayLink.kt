package com.squelch.app.mesh.online

import com.squelch.app.mesh.MeshLink
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * A single [MeshLink] to a Squelch relay over WebSocket.
 *
 * Protocol (text frames over WS):
 *
 *   { "t":"m", "k":1,  "f":"<edPubHex>", "p":"<base64>" }   (HELLO/HS/DATA)
 *   { "t":"hi", "peers":[<edPubHex>, ...] }                  (peer roster, server -> client)
 *   { "t":"bye", "f":"<edPubHex>" }                         (peer gone)
 *
 * The relay is a tiny fan-out service (see docs/relay-protocol.md) that
 * authenticates each client via the OAuth bearer token acquired during
 * Google Sign-In. Encryption remains end-to-end: the relay only sees
 * ciphertext bytes for kind 0x02.
 */
class WebSocketRelayLink(
    private val relayUrl: String,
    private val bearerToken: String,
    private val localEdPubHex: String,
    private val listener: Listener
) : MeshLink {

    interface Listener {
        fun onFrame(kind: Byte, payload: ByteArray)
        fun onOpen()
        fun onClosed(reason: String)
    }

    override val transportName = "relay"
    override val remoteId = localEdPubHex  // a "link" is per-local-identity at this layer

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    fun connect() {
        val req = Request.Builder()
            .url("$relayUrl/mesh")
            .addHeader("Authorization", "Bearer $bearerToken")
            .addHeader("X-Squelch-EdPub", localEdPubHex)
            .build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed("closed code=$code reason=$reason")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onClosed("failure: ${t.message}")
            }
        })
    }

    private fun handleFrame(text: String) {
        val msg = try { JSONObject(text) } catch (_: Exception) { return }
        when (msg.optString("t")) {
            "m" -> {
                val kind = msg.optInt("k").toByte()
                val payload = try {
                    Base64.getDecoder().decode(msg.optString("p"))
                } catch (_: Exception) {
                    return
                }
                listener.onFrame(kind, payload)
            }
            "hi", "bye", "peers" -> {
                // relay-side bookkeeping; ignored by the engine for v0.10.
            }
        }
    }

    override fun sendFrame(kind: Byte, body: ByteArray) {
        val payload = Base64.getEncoder().encodeToString(body)
        val msg = JSONObject().apply {
            put("t", "m")
            put("k", kind.toInt() and 0xff)
            put("f", localEdPubHex)
            put("p", payload)
        }
        socket?.send(msg.toString())
    }

    override fun close() {
        socket?.close(1000, "client closed")
        socket = null
    }
}