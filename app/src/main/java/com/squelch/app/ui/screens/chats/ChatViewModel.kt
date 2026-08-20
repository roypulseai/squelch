package com.squelch.app.ui.screens.chats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.BlockedEntity
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.GroupEntity
import com.squelch.app.data.local.entity.GroupMemberEntity
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    val groups: StateFlow<List<GroupEntity>> =
        vaultRepository.dbReady.flatMapLatest { db ->
            db?.groups()?.observeAll() ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val strangers: StateFlow<List<StrangerConversation>> =
        vaultRepository.dbReady.flatMapLatest { db ->
            if (db == null) return@flatMapLatest flowOf(emptyList())
            db.messages().observeAll().flatMapLatest { allMessages ->
                val contactPubkeys = try {
                    db.contacts().pubkeys().toSet()
                } catch (_: Exception) { emptySet() }
                val selfPub = messageRelay.selfEdPubHex

                val strangerMap = mutableMapOf<String, MutableList<MessageEntity>>()
                for (msg in allMessages) {
                    if (msg.sender == selfPub) continue
                    if (msg.sender in contactPubkeys) continue
                    if (msg.conversationId == msg.sender) {
                        strangerMap.getOrPut(msg.sender) { mutableListOf() }.add(msg)
                    }
                }

                val result = strangerMap.map { (senderPub, msgs) ->
                    val sorted = msgs.sortedByDescending { it.timestamp }
                    val contact = try { db.contacts().get(senderPub) } catch (_: Exception) { null }
                    StrangerConversation(
                        senderEdPubHex = senderPub,
                        senderName = contact?.displayName?.ifEmpty { contact.callsign }
                            ?: sorted.firstOrNull()?.body?.take(20)
                            ?: senderPub.take(8),
                        lastMessage = sorted.firstOrNull()?.body ?: "",
                        timestamp = sorted.firstOrNull()?.timestamp ?: 0,
                        unreadCount = sorted.count { it.direction == 0 }
                    )
                }.sortedByDescending { it.timestamp }
                flowOf(result)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val messageFlows = mutableMapOf<String, StateFlow<List<MessageEntity>>>()

    fun messages(conversationId: String): StateFlow<List<MessageEntity>> =
        messageFlows.getOrPut(conversationId) {
            val flow = vaultRepository.db?.messages()?.observeByConversation(conversationId)
                ?: flowOf(emptyList())
            flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun sendMessage(
        conversationId: String,
        recipientUid: String,
        senderName: String,
        plaintext: String
    ) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch

            val msg = MessageEntity(
                conversationId = conversationId,
                msgId = UUID.randomUUID().toString(),
                sender = messageRelay.selfEdPubHex,
                body = plaintext,
                timestamp = System.currentTimeMillis(),
                direction = 1,
                delivery = 0,
                kind = 2
            )
            database.messages().insert(msg)

            val existingConv = database.conversations().get(conversationId)
            if (existingConv == null) {
                database.conversations().upsert(
                    ConversationEntity(
                        id = conversationId,
                        name = senderName,
                        lastMessagePreview = plaintext.take(80),
                        lastMessageTimestamp = msg.timestamp,
                        unreadCount = 0
                    )
                )
            } else {
                database.conversations().updateLastMessage(
                    id = conversationId,
                    preview = plaintext.take(80),
                    timestamp = msg.timestamp
                )
            }

            if (messageRelay.isRunning) {
                messageRelay.sendMessage(
                    recipientEdPubHex = conversationId,
                    recipientUid = recipientUid,
                    senderName = senderName,
                    plaintext = plaintext,
                    db = database
                )
            } else {
                Log.w("ChatViewModel", "MessageRelay not running, stored locally only")
            }
        }
    }

    fun sendGroupMessage(
        groupId: String,
        groupName: String,
        plaintext: String
    ) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch

            val msg = MessageEntity(
                conversationId = groupId,
                msgId = UUID.randomUUID().toString(),
                sender = messageRelay.selfEdPubHex,
                body = plaintext,
                timestamp = System.currentTimeMillis(),
                direction = 1,
                delivery = 0,
                kind = 2
            )
            database.messages().insert(msg)

            database.groups().updateLastMessage(
                groupId = groupId,
                preview = plaintext.take(80),
                timestamp = msg.timestamp
            )

            val members = try { database.groups().getMembers(groupId) } catch (_: Exception) { emptyList() }

            if (messageRelay.isRunning) {
                for (member in members) {
                    if (member.edPubHex == messageRelay.selfEdPubHex) continue
                    try {
                        val contact = database.contacts().get(member.edPubHex)
                        val firebaseUid = contact?.firebaseUid ?: ""
                        messageRelay.sendMessage(
                            recipientEdPubHex = member.edPubHex,
                            recipientUid = firebaseUid,
                            senderName = groupName,
                            plaintext = "[${groupName}] ${messageRelay.getContactName(database)}: $plaintext",
                            db = database
                        )
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed to send to ${member.edPubHex}: ${e.message}")
                    }
                }
            }
        }
    }

    fun createGroup(name: String, memberPubKeys: List<String>) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            val groupId = "group_${UUID.randomUUID()}"
            val selfPub = messageRelay.selfEdPubHex

            database.groups().upsert(
                GroupEntity(
                    id = groupId,
                    name = name,
                    createdBy = selfPub
                )
            )

            database.groups().addMember(
                GroupMemberEntity(
                    groupId = groupId,
                    edPubHex = selfPub,
                    displayName = "You"
                )
            )

            for (pubKey in memberPubKeys) {
                val contact = database.contacts().get(pubKey)
                database.groups().addMember(
                    GroupMemberEntity(
                        groupId = groupId,
                        edPubHex = pubKey,
                        displayName = contact?.displayName?.ifEmpty { pubKey.take(8) } ?: pubKey.take(8)
                    )
                )
            }
        }
    }

    fun blockSender(edPubHex: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            val contact = database.contacts().get(edPubHex)
            database.blocked().block(
                BlockedEntity(
                    edPubHex = edPubHex,
                    displayName = contact?.displayName ?: edPubHex.take(8)
                )
            )
        }
    }

    fun createConversation(id: String, name: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            val existing = database.conversations().get(id)
            if (existing == null) {
                database.conversations().upsert(
                    ConversationEntity(
                        id = id,
                        name = name,
                        lastMessageTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun clearUnread(conversationId: String) {
        viewModelScope.launch {
            vaultRepository.db?.conversations()?.clearUnread(conversationId)
        }
    }

    fun clearGroupUnread(groupId: String) {
        viewModelScope.launch {
            vaultRepository.db?.groups()?.clearUnread(groupId)
        }
    }
}
