package com.squelch.app.mesh.online

import com.squelch.app.auth.AuthState
import com.squelch.app.mesh.Hello
import com.squelch.app.mesh.InnerMessage
import com.squelch.app.mesh.MeshPacket
import com.squelch.app.util.Bytes
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * libp2p-style online mesh extension (M-online, v0.10).
 *
 *   - Authenticates via the same Google OAuth token that drives Drive.
 *   - Publishes a HELLO so the relay can show our edPub in the roster.
 *   - Receives frames from the relay and decodes them as MeshPackets.
 *
 * This is a working minimum: the engine treats the relay the same way
 * it treats a Nearby link. A future Rust core (rust-libp2p swarm +
 * STUN + Circuit Relay v2) can replace this transport with byte-for-byte
 * the same MeshLink surface.
 */
class RelayTransport(
    private val context: android.content.Context,
    private val identity: () -> com.squelch.app.crypto.Identity?,
    private val relayUrl: String = DEFAULT_RELAY_URL
) {
    companion object {
        const val DEFAULT_RELAY_URL = "wss://relay.squelch.app/v2"
        const val SCOPE_DRIVE_FILE = "oauth2:https://www.googleapis.com/auth/drive.file"
    }

    private val _status = MutableStateFlow(RelayStatus())
    val status: StateFlow<RelayStatus> = _status.asStateFlow()

    private var link: WebSocketRelayLink? = null

    fun start(signed: AuthState.SignedIn, sendToEngine: (Byte, ByteArray) -> Unit) {
        val id = identity() ?: run {
            _status.value = RelayStatus(error = "vault locked")
            return
        }
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: run {
                _status.value = RelayStatus(error = "no Google account")
                return
            }
        val token = try {
            GoogleAuthUtil.getToken(context, account, SCOPE_DRIVE_FILE)
        } catch (e: Exception) {
            _status.value = RelayStatus(error = "token: ${e.message}")
            return
        }

        val edPubHex = Bytes.hex(id.edPub)
        _status.value = RelayStatus(url = relayUrl, connecting = true)

        val newLink = WebSocketRelayLink(
            relayUrl = "$relayUrl/mesh",
            bearerToken = token,
            localEdPubHex = edPubHex,
            listener = object : WebSocketRelayLink.Listener {
                override fun onFrame(kind: Byte, payload: ByteArray) {
                    sendToEngine(kind, payload)
                }
                override fun onOpen() {
                    _status.value = _status.value.copy(connected = true, connecting = false)
                    // Publish a HELLO so the relay knows our edPub and any
                    // other clients can route us messages.
                    val hello = Hello(
                        edPub = id.edPub,
                        xPub = id.xPub,
                        callsign = Hello.callsignFor(id.edPub, id.xPub),
                        capabilities = Hello.CAP_PLAIN or Hello.CAP_AES_GCM,
                        deviceName = android.os.Build.MODEL
                    )
                    link?.sendFrame(com.squelch.app.mesh.MeshEngine.KIND_HELLO, hello.encode())
                }
                override fun onClosed(reason: String) {
                    _status.value = _status.value.copy(
                        connected = false,
                        connecting = false,
                        error = reason
                    )
                }
            }
        )
        link = newLink
        newLink.connect()
    }

    fun stop() {
        link?.close()
        link = null
        _status.value = RelayStatus()
    }

    fun send(kind: Byte, payload: ByteArray) {
        link?.sendFrame(kind, payload)
    }

    data class RelayStatus(
        val url: String = DEFAULT_RELAY_URL,
        val connecting: Boolean = false,
        val connected: Boolean = false,
        val error: String? = null
    )
}