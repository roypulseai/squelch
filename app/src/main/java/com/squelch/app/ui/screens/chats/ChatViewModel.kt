package com.squelch.app.ui.screens.chats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.mesh.relay.MessageRelay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val messageRelay: MessageRelay
) : ViewModel() {

    private val db: SquelchDatabase? get() = vaultRepository.db

    val conversations: StateFlow<List<ConversationEntity>> =
        vaultRepository.dbReady.flatMapLatest { db ->
            db?.conversations()?.observeAll() ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val messageFlows = mutableMapOf<String, StateFlow<List<MessageEntity>>>()

    fun messages(conversationId: String): StateFlow<List<MessageEntity>> =
        messageFlows.getOrPut(conversationId) {
            val db = vaultRepository.db
            val flow = db?.messages()?.observeByConversation(conversationId)
                ?: flowOf(emptyList())
            flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun sendMessage(conversationId: String, senderEdPubHex: String, plaintext: String) {
        viewModelScope.launch {
            val db = vaultRepository.db ?: return@launch

            val msg = MessageEntity(
                conversationId = conversationId,
                msgId = UUID.randomUUID().toString(),
                sender = senderEdPubHex,
                body = plaintext,
                timestamp = System.currentTimeMillis(),
                direction = 1,
                delivery = 0,
                kind = 2
            )
            db.messages().insert(msg)
            db.conversations().updateLastMessage(
                id = conversationId,
                preview = plaintext.take(80),
                timestamp = msg.timestamp
            )

            if (messageRelay.isRunning) {
                messageRelay.send(
                    recipientEdPubHex = conversationId,
                    plaintext = plaintext,
                    senderEdPubHex = senderEdPubHex
                )
            } else {
                Log.w("ChatViewModel", "MessageRelay not running, message stored locally only")
            }
        }
    }

    fun createConversation(id: String, name: String) {
        viewModelScope.launch {
            vaultRepository.db?.conversations()?.upsert(
                ConversationEntity(
                    id = id,
                    name = name,
                    lastMessageTimestamp = System.currentTimeMillis()
                )
            )
        }
    }
}
