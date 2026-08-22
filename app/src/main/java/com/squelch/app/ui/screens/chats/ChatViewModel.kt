package com.squelch.app.ui.screens.chats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.local.entity.BlockedEntity
import com.squelch.app.data.local.entity.ContactEntity
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.GroupEntity
import com.squelch.app.data.local.entity.SettingEntity
import com.squelch.app.data.local.entity.GroupMemberEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.mesh.engine.MeshEngineManager
import com.squelch.app.mesh.relay.MessageRelay
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.translate.TranslationManager
import com.squelch.app.translate.TranslationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val messageRelay: MessageRelay,
    private val meshEngineManager: MeshEngineManager
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val db: SquelchDatabase? get() = vaultRepository.db

    init {
        viewModelScope.launch {
            for (event in messageRelay.blockEvents) {
                val database = db ?: continue
                val contact = try {
                    database.contacts().get(event.peerEdPubHex)
                } catch (_: Exception) { null }
                val resolvedName = contact?.displayName?.ifEmpty { event.peerEdPubHex.take(8) }
                    ?: event.peerEdPubHex.take(8)

                val body = if (event.blocked) {
                    "You've been blocked by $resolvedName"
                } else {
                    "You've been unblocked by $resolvedName"
                }

                val message = MessageEntity(
                    conversationId = event.peerEdPubHex,
                    msgId = UUID.randomUUID().toString(),
                    sender = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    direction = 2,
                    delivery = 1,
                    kind = 2
                )
                database.messages().insert(message)

                val existingConv = database.conversations().get(event.peerEdPubHex)
                if (existingConv == null) {
                    database.conversations().upsert(
                        ConversationEntity(
                            id = event.peerEdPubHex,
                            name = resolvedName,
                            lastMessagePreview = body,
                            lastMessageTimestamp = message.timestamp,
                            unreadCount = 1
                        )
                    )
                } else {
                    database.conversations().updateLastMessage(
                        id = event.peerEdPubHex,
                        preview = body,
                        timestamp = message.timestamp
                    )
                }
                Log.d(TAG, "Stored block event for $resolvedName")
            }
        }
    }

    val conversations: StateFlow<List<ConversationEntity>> =
        vaultRepository.dbReady.flatMapLatest { db ->
            db?.conversations()?.observeAll() ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _preferredLang = kotlinx.coroutines.flow.MutableStateFlow("en")
    val preferredLang: kotlinx.coroutines.flow.StateFlow<String> = _preferredLang

    private val _showTranslation = kotlinx.coroutines.flow.MutableStateFlow(false)
    val showTranslation: kotlinx.coroutines.flow.StateFlow<Boolean> = _showTranslation

    private val translationCache = ConcurrentHashMap<String, TranslationResult>()

    init {
        viewModelScope.launch {
            loadLanguageSettings()
        }
    }

    private suspend fun loadLanguageSettings() {
        val db = vaultRepository.db ?: return
        try {
            val lang = db.settings().get("preferred_language")
            if (lang != null) _preferredLang.value = lang
            val showTrans = db.settings().get("show_translation")
            _showTranslation.value = showTrans == "true"
        } catch (_: Exception) {}
    }

    fun setPreferredLanguage(langCode: String) {
        _preferredLang.value = langCode
        viewModelScope.launch {
            val db = vaultRepository.db ?: return@launch
            db.settings().put(SettingEntity(key = "preferred_language", value = langCode))
            translationCache.clear()
        }
    }

    fun toggleTranslation() {
        _showTranslation.value = !_showTranslation.value
        viewModelScope.launch {
            val db = vaultRepository.db ?: return@launch
            db.settings().put(SettingEntity(key = "show_translation", value = _showTranslation.value.toString()))
        }
    }

    fun getDisplayText(msg: MessageEntity, onTranslated: (String) -> Unit) {
        if (!_showTranslation.value || msg.direction == 1) {
            onTranslated(msg.body)
            return
        }
        val cached = translationCache[msg.msgId]
        if (cached != null) {
            onTranslated(cached.translated ?: cached.original)
            return
        }
        viewModelScope.launch {
            val result = TranslationManager.translateIfNeeded(msg.body, _preferredLang.value)
            translationCache[msg.msgId] = result
            onTranslated(result.translated ?: result.original)
        }
    }

    val contacts: StateFlow<List<ContactEntity>> =
        vaultRepository.dbReady.flatMapLatest { db ->
            db?.contacts()?.observeAll() ?: flowOf(emptyList())
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
                    msgId = msg.msgId
                )
            } else {
                Log.w("ChatViewModel", "MessageRelay not running, stored locally only")
            }

            try {
                meshEngineManager.get()?.sendMessage(
                    recipientEdPubHex = conversationId,
                    plaintext = plaintext.toByteArray(Charsets.UTF_8)
                )
            } catch (e: Exception) {
                Log.d("ChatViewModel", "Mesh send failed (non-critical): ${e.message}")
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
                            msgId = msg.msgId
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
                    displayName = "You",
                    role = GroupMemberEntity.ROLE_ADMIN
                )
            )

            for (pubKey in memberPubKeys) {
                val contact = database.contacts().get(pubKey)
                database.groups().addMember(
                    GroupMemberEntity(
                        groupId = groupId,
                        edPubHex = pubKey,
                        displayName = contact?.displayName?.ifEmpty { pubKey.take(8) } ?: pubKey.take(8),
                        role = GroupMemberEntity.ROLE_MEMBER
                    )
                )
            }

            database.conversations().upsert(
                ConversationEntity(
                    id = groupId,
                    name = name,
                    lastMessagePreview = "Group created",
                    lastMessageTimestamp = System.currentTimeMillis(),
                    type = 1
                )
            )
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
            val recipientUid = contact?.firebaseUid ?: ""
            if (messageRelay.isRunning && recipientUid.isNotEmpty()) {
                val cmd = JSONObject().apply { put("cmd", "blocked") }
                messageRelay.sendCommand(
                    recipientEdPubHex = edPubHex,
                    recipientUid = recipientUid,
                    senderName = "",
                    kind = Transport.TransportFrame.KIND_BLOCKED,
                    payloadBytes = cmd.toString().toByteArray(Charsets.UTF_8)
                )
            }
        }
    }

    fun unblockSender(edPubHex: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            database.blocked().unblock(edPubHex)
            val contact = database.contacts().get(edPubHex)
            val recipientUid = contact?.firebaseUid ?: ""
            if (messageRelay.isRunning && recipientUid.isNotEmpty()) {
                val cmd = JSONObject().apply { put("cmd", "unblocked") }
                messageRelay.sendCommand(
                    recipientEdPubHex = edPubHex,
                    recipientUid = recipientUid,
                    senderName = "",
                    kind = Transport.TransportFrame.KIND_UNBLOCKED,
                    payloadBytes = cmd.toString().toByteArray(Charsets.UTF_8)
                )
            }
        }
    }

    val blockedUsers: StateFlow<List<BlockedEntity>> =
        vaultRepository.dbReady.flatMapLatest { db ->
            if (db == null) return@flatMapLatest flowOf(emptyList())
            db.blocked().observeAll()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun isBlocked(edPubHex: String): Boolean {
        val database = vaultRepository.db ?: return false
        return try {
            database.blocked().get(edPubHex) != null
        } catch (_: Exception) { false }
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

    fun getMemberName(pubkey: String): String {
        if (pubkey == messageRelay.selfEdPubHex) return "You"
        val db = vaultRepository.db ?: return pubkey.take(8)
        return try {
            kotlinx.coroutines.runBlocking {
                val contact = db.contacts().get(pubkey)
                contact?.displayName?.ifEmpty { contact.callsign.ifEmpty { pubkey.take(8) } }
                    ?: pubkey.take(8)
            }
        } catch (_: Exception) { pubkey.take(8) }
    }

    fun isContact(pubkey: String): Boolean {
        val db = vaultRepository.db ?: return false
        return try {
            kotlinx.coroutines.runBlocking { db.contacts().get(pubkey) != null }
        } catch (_: Exception) { false }
    }

    fun addContact(pubkey: String, displayName: String, email: String, firebaseUid: String, xPub: String) {
        viewModelScope.launch {
            val db = vaultRepository.db ?: return@launch
            db.contacts().upsert(
                ContactEntity(
                    pubkey = pubkey,
                    firebaseUid = firebaseUid,
                    xPub = xPub,
                    callsign = displayName,
                    displayName = displayName,
                    email = email,
                    lastSeen = System.currentTimeMillis()
                )
            )
            vaultRepository.pushContactsToCloud()
        }
    }

    fun syncAllContacts() {
        viewModelScope.launch {
            vaultRepository.pushContactsToCloud()
            vaultRepository.syncContactsFromCloud()
        }
    }

    fun getSelfPubkey(): String = messageRelay.selfEdPubHex

    private val groupMemberFlows = mutableMapOf<String, StateFlow<List<GroupMemberEntity>>>()

    fun getGroupMembers(groupId: String): StateFlow<List<GroupMemberEntity>> =
        groupMemberFlows.getOrPut(groupId) {
            vaultRepository.dbReady.flatMapLatest { db ->
                if (db == null) return@flatMapLatest flowOf(emptyList())
                db.groups().observeMembers(groupId)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    private val groupFlows = mutableMapOf<String, StateFlow<GroupEntity?>>()

    fun observeGroup(groupId: String): StateFlow<GroupEntity?> =
        groupFlows.getOrPut(groupId) {
            vaultRepository.dbReady.flatMapLatest { db ->
                if (db == null) return@flatMapLatest flowOf(null)
                db.groups().observeGroup(groupId)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        }

    fun renameGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            vaultRepository.db?.groups()?.updateName(groupId, newName)
        }
    }

    fun toggleMuteGroup(groupId: String, muted: Boolean) {
        viewModelScope.launch {
            vaultRepository.db?.groups()?.setMuted(groupId, muted)
        }
    }

    fun removeMemberFromGroup(groupId: String, edPubHex: String) {
        viewModelScope.launch {
            vaultRepository.db?.groups()?.removeMember(groupId, edPubHex)
        }
    }

    fun promoteToAdmin(groupId: String, edPubHex: String) {
        viewModelScope.launch {
            vaultRepository.db?.groups()?.setRole(groupId, edPubHex, GroupMemberEntity.ROLE_ADMIN)
        }
    }

    fun demoteFromAdmin(groupId: String, edPubHex: String) {
        viewModelScope.launch {
            vaultRepository.db?.groups()?.setRole(groupId, edPubHex, GroupMemberEntity.ROLE_MEMBER)
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            val selfPub = messageRelay.selfEdPubHex
            val members = database.groups().getMembers(groupId)

            database.groups().removeMember(groupId, selfPub)

            val selfMember = members.find { it.edPubHex == selfPub }
            if (selfMember?.role == GroupMemberEntity.ROLE_ADMIN) {
                val remaining = database.groups().getMembers(groupId)
                if (remaining.isNotEmpty()) {
                    val oldest = remaining.minByOrNull { it.joinedAt }
                    if (oldest != null) {
                        database.groups().setRole(groupId, oldest.edPubHex, GroupMemberEntity.ROLE_ADMIN)
                    }
                } else {
                    database.messages().purgeForConversation(groupId)
                    database.groups().delete(groupId)
                }
            }
        }
    }

    fun addMemberToGroup(groupId: String, edPubHex: String, displayName: String) {
        viewModelScope.launch {
            vaultRepository.db?.groups()?.addMember(
                GroupMemberEntity(
                    groupId = groupId,
                    edPubHex = edPubHex,
                    displayName = displayName,
                    role = GroupMemberEntity.ROLE_MEMBER
                )
            )
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            database.messages().purgeForConversation(groupId)
            database.groups().delete(groupId)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            database.messages().purgeForConversation(conversationId)
            database.conversations().delete(conversationId)
        }
    }

    fun togglePin(conversationId: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            val conv = database.conversations().get(conversationId) ?: return@launch
            database.conversations().setPinned(conversationId, !conv.pinned)
        }
    }

    fun toggleMute(conversationId: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            val conv = database.conversations().get(conversationId) ?: return@launch
            database.conversations().setMuted(conversationId, !conv.muted)
        }
    }

    fun deleteContact(pubkey: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            database.contacts().delete(pubkey)
            database.messages().purgeForConversation(pubkey)
            database.conversations().delete(pubkey)
            vaultRepository.pushContactsToCloud()
        }
    }

    fun recallMessage(msgId: String, conversationId: String, recipientUid: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            database.messages().delete(msgId)

            val payload = JSONObject().apply {
                put("cmd", "recall")
                put("msgId", msgId)
            }.toString().toByteArray(Charsets.UTF_8)

            messageRelay.sendCommand(
                recipientEdPubHex = conversationId,
                recipientUid = recipientUid,
                senderName = "",
                kind = Transport.TransportFrame.KIND_RECALL,
                payloadBytes = payload
            )
        }
    }

    fun editMessage(msgId: String, newText: String, conversationId: String, recipientUid: String) {
        viewModelScope.launch {
            val database = vaultRepository.db ?: return@launch
            database.messages().updateBody(msgId, newText)

            val payload = JSONObject().apply {
                put("cmd", "edit")
                put("msgId", msgId)
                put("text", newText)
            }.toString().toByteArray(Charsets.UTF_8)

            messageRelay.sendCommand(
                recipientEdPubHex = conversationId,
                recipientUid = recipientUid,
                senderName = "",
                kind = Transport.TransportFrame.KIND_EDIT,
                payloadBytes = payload
            )
        }
    }

    fun forwardMessage(
        originalBody: String,
        conversationId: String,
        recipientUid: String,
        senderName: String
    ) {
        sendMessage(
            conversationId = conversationId,
            recipientUid = recipientUid,
            senderName = senderName,
            plaintext = originalBody
        )
    }
}
