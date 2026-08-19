package com.squelch.app.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val db: SquelchDatabase? get() = vaultRepository.db

    val conversations: StateFlow<List<ConversationEntity>> by lazy {
        val flow = db?.conversations()?.observeAll()
            ?: MutableStateFlow(emptyList())
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun messages(conversationId: String): StateFlow<List<MessageEntity>> {
        val flow = db?.messages()?.observeByConversation(conversationId)
            ?: MutableStateFlow(emptyList())
        return flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun sendMessage(conversationId: String, sender: String, plaintext: String) {
        viewModelScope.launch {
            val db = db ?: return@launch
            val msg = MessageEntity(
                conversationId = conversationId,
                msgId = UUID.randomUUID().toString(),
                sender = sender,
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
        }
    }

    fun createConversation(id: String, name: String) {
        viewModelScope.launch {
            db?.conversations()?.upsert(
                ConversationEntity(
                    id = id,
                    name = name,
                    lastMessageTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun receiveMessage(conversationId: String, sender: String, body: String) {
        viewModelScope.launch {
            val db = db ?: return@launch
            val msg = MessageEntity(
                conversationId = conversationId,
                msgId = UUID.randomUUID().toString(),
                sender = sender,
                body = body,
                timestamp = System.currentTimeMillis(),
                direction = 0,
                delivery = 1,
                kind = 2
            )
            db.messages().insert(msg)
            db.conversations().updateLastMessage(
                id = conversationId,
                preview = body.take(80),
                timestamp = msg.timestamp
            )
            db.conversations().incrementUnread(conversationId)
        }
    }
}
