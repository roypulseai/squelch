package com.squelch.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.squelch.app.SquelchApp
import com.squelch.app.db.ContactEntity
import com.squelch.app.db.ConversationEntity
import com.squelch.app.db.MessageEntity
import com.squelch.app.db.RoomEntity
import com.squelch.app.mesh.MeshEngine
import com.squelch.app.mesh.MeshService
import com.squelch.app.util.Bytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class SquelchViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app as SquelchApp
    private val db = appContext.database
    val engine: MeshEngine = appContext.engine

    val myCallsign: String get() = engine.callsign
    val myFingerprint: String get() = Bytes.hex(engine.identity.fingerprint.copyOf(6)).uppercase()

    val contacts: StateFlow<List<ContactEntity>> =
        db.contacts().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> =
        db.conversations().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rooms: StateFlow<List<RoomEntity>> =
        db.rooms().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _meshStatus = MutableStateFlow(engine.status)
    val meshStatus: StateFlow<MeshEngine.MeshStatus> = _meshStatus

    init {
        viewModelScope.launch {
            while (true) {
                _meshStatus.value = engine.status
                delay(2000)
            }
        }
    }

    fun messagesFor(conversationId: String): Flow<List<MessageEntity>> =
        db.messages().observeByConversation(conversationId)

    fun sendDm(peerEdHex: String, text: String) {
        val peer = engine.peers.getByHex(peerEdHex) ?: return
        engine.messageLayer.sendDm(peer, text)
    }

    fun sendRoomMessage(roomIdHex: String, text: String) {
        val room = engine.rooms.findById(Bytes.unhex(roomIdHex)) ?: return
        engine.messageLayer.sendRoomMessage(room, text)
    }

    fun joinRoom(name: String, passphrase: String) {
        viewModelScope.launch {
            val room = engine.rooms.join(name, passphrase)
            engine.sendRoomJoin(room)
        }
    }

    fun leaveRoom(roomIdHex: String) {
        viewModelScope.launch {
            engine.rooms.leave(roomIdHex)
        }
    }

    fun purgeOlderThan(retentionHours: Long) {
        engine.applyHistoryRetention(retentionHours * 3600 * 1000)
    }

    fun meshRunning(): Boolean = engine.started

    fun startMesh() {
        if (!engine.started) MeshService.start(getApplication())
    }

    fun stopMesh() {
        if (engine.started) MeshService.stop(getApplication())
    }

    fun toggleMesh() {
        if (engine.started) stopMesh() else startMesh()
    }

    fun onNfcIdentityRead(edPub: ByteArray, xPub: ByteArray, nonce: ByteArray) {
        engine.onNfcIdentityRead(edPub, xPub, nonce)
    }

    fun exportIdentity(): String = appContext.identityManager.exportBase64()

    fun importIdentity(encoded: String): Boolean = appContext.identityManager.importBase64(encoded)
}
