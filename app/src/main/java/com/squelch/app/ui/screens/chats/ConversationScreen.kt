package com.squelch.app.ui.screens.chats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.squelch.app.data.local.entity.ConversationEntity
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.ui.theme.Accent
import com.squelch.app.ui.theme.DarkOnSurface
import com.squelch.app.ui.theme.LightOnSurface
import com.squelch.app.ui.theme.ReceivedBubble
import com.squelch.app.ui.theme.ReceivedBubbleLight
import com.squelch.app.ui.theme.SentBubble
import com.squelch.app.ui.theme.SentBubbleLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    conversationName: String,
    selfPubkey: String,
    recipientUid: String = "",
    recipientEmail: String = "",
    networkType: String = "Internet",
    isGroup: Boolean = false,
    onBack: () -> Unit = {},
    onGroupInfoClick: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages(conversationId).collectAsState()
    var inputField by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showUserInfo by remember { mutableStateOf(false) }
    val userInfoSheetState = rememberModalBottomSheetState()
    var isRecipientBlocked by remember { mutableStateOf(false) }
    var isRecipientContact by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        isRecipientBlocked = viewModel.isBlocked(conversationId)
        isRecipientContact = viewModel.isContact(conversationId)
    }

    var showActionsFor by remember { mutableStateOf<MessageEntity?>(null) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingMsg by remember { mutableStateOf<MessageEntity?>(null) }
    var editText by remember { mutableStateOf("") }

    var showRecallConfirm by remember { mutableStateOf(false) }
    var recallingMsg by remember { mutableStateOf<MessageEntity?>(null) }

    var showForwardSheet by remember { mutableStateOf(false) }
    var forwardingMsg by remember { mutableStateOf<MessageEntity?>(null) }

    val groupMembers by viewModel.getGroupMembers(conversationId).collectAsState()
    var mentionQuery by remember { mutableStateOf("") }
    var showMentionDropdown by remember { mutableStateOf(false) }
    var mentionStartIndex by remember { mutableIntStateOf(-1) }

    val inputText = inputField.text

    val showTranslation by viewModel.showTranslation.collectAsState()
    val preferredLang by viewModel.preferredLang.collectAsState()

    val isPeerTyping = if (!isGroup) {
        val typingPeers by viewModel.typingPeers.collectAsState()
        val lastTyping = typingPeers[conversationId]
        lastTyping != null && (System.currentTimeMillis() - lastTyping) < 5000
    } else false

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.clearUnread(conversationId)
    }

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.body.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            onClick = {
                                if (isGroup) onGroupInfoClick() else showUserInfo = true
                            }
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conversationName.firstOrNull()?.uppercase() ?: "?",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversationName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isGroup -> "group \u00b7 ${groupMembers.size} members"
                                    isPeerTyping -> "typing..."
                                    else -> "online"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPeerTyping) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        showSearch = !showSearch
                                        if (!showSearch) searchQuery = ""
                                    }
                                    .padding(end = 4.dp),
                                tint = if (showSearch) Accent
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Filled.Translate,
                                contentDescription = "Translation",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.toggleTranslation() }
                                    .padding(end = 4.dp),
                                tint = if (showTranslation) Accent
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.SignalWifi4Bar,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = networkType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .navigationBarsPadding()
        ) {
            if (showSearch) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search messages...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                                inner()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${filteredMessages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(filteredMessages, key = { _, msg -> msg.msgId }) { index, msg ->
                    val isSelf = msg.direction == 1
                    val prevMsg = filteredMessages.getOrNull(index - 1)
                    val nextMsg = filteredMessages.getOrNull(index + 1)

                    val showTimestampHeader = shouldShowTimestampHeader(prevMsg, msg)

                    if (showTimestampHeader) {
                        TimestampHeader(msg.timestamp)
                    }

                    val isLastInGroup = nextMsg == null ||
                        nextMsg.direction != msg.direction ||
                        shouldShowTimestampHeader(msg, nextMsg)

                    val isFirstInGroup = prevMsg == null ||
                        prevMsg.direction != msg.direction ||
                        shouldShowTimestampHeader(prevMsg, msg)

                    MessageBubble(
                        msg = msg,
                        isSelf = isSelf,
                        isLastInGroup = isLastInGroup,
                        isFirstInGroup = isFirstInGroup,
                        showSender = isGroup && !isSelf,
                        senderName = viewModel.getMemberName(msg.sender),
                        showTranslation = showTranslation && !isSelf,
                        preferredLang = preferredLang,
                        onLongPress = { showActionsFor = msg }
                    )
                }
            }

            if (showMentionDropdown && isGroup) {
                val filtered = groupMembers.filter { member ->
                    member.displayName.contains(mentionQuery, ignoreCase = true) ||
                        member.edPubHex.take(8).contains(mentionQuery, ignoreCase = true)
                }
                if (filtered.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 180.dp)
                        ) {
                            items(filtered, key = { it.edPubHex }) { member ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val name = member.displayName.ifEmpty { member.edPubHex.take(8) }
                                                val before = inputText.substring(0, mentionStartIndex)
                                                val after = inputText.substring(inputField.selection.end)
                                                val newCursorPos = before.length + name.length + 1
                                            inputField = TextFieldValue(
                                                text = "${before}@${name} $after",
                                                selection = TextRange(newCursorPos)
                                            )
                                            showMentionDropdown = false
                                            mentionQuery = ""
                                            mentionStartIndex = -1
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val displayName = member.displayName.ifEmpty { member.edPubHex.take(8) }
                                            Text(
                                                text = displayName.firstOrNull()?.uppercase() ?: "?",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        val displayName = member.displayName.ifEmpty { member.edPubHex.take(8) }
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Type a message",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        BasicTextField(
                            value = inputField,
                            onValueChange = { newValue ->
                                val oldText = inputField.text
                                val newText = newValue.text
                                val cursorPos = newValue.selection.end

                                inputField = newValue

                                if (isGroup && groupMembers.isNotEmpty()) {
                                    val atPos = newText.lastIndexOf('@', cursorPos - 1)
                                    if (atPos >= 0 && (atPos == 0 || newText[atPos - 1] == ' ' || newText[atPos - 1] == '\n')) {
                                        val query = newText.substring(atPos + 1, cursorPos)
                                        if (!query.contains(' ') && !query.contains('\n')) {
                                            mentionQuery = query
                                            mentionStartIndex = atPos
                                            showMentionDropdown = true
                                        } else {
                                            showMentionDropdown = false
                                        }
                                    } else {
                                        showMentionDropdown = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 4
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                if (isGroup) {
                                    viewModel.sendGroupMessage(
                                        groupId = conversationId,
                                        groupName = conversationName,
                                        plaintext = inputText.trim()
                                    )
                                } else {
                                    viewModel.sendMessage(
                                        conversationId = conversationId,
                                        recipientUid = recipientUid,
                                        senderName = conversationName,
                                        plaintext = inputText.trim()
                                    )
                                }
                                inputField = TextFieldValue("")
                                showMentionDropdown = false
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank()) Accent
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showUserInfo && !isGroup) {
        ModalBottomSheet(
            onDismissRequest = { showUserInfo = false },
            sheetState = userInfoSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversationName.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = conversationName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (recipientEmail.isNotEmpty()) {
                            InfoRow(label = "Email", value = recipientEmail)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                if (!isRecipientContact && !isGroup) {
                    Button(
                        onClick = {
                            viewModel.addContact(
                                pubkey = conversationId,
                                displayName = conversationName,
                                email = recipientEmail,
                                firebaseUid = recipientUid,
                                xPub = ""
                            )
                            isRecipientContact = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Contacts")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (isRecipientBlocked) {
                    OutlinedButton(
                        onClick = {
                            viewModel.unblockSender(conversationId)
                            isRecipientBlocked = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unblock User")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            viewModel.blockSender(conversationId)
                            isRecipientBlocked = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Block User")
                    }
                }
            }
        }
    }

    showActionsFor?.let { msg ->
        MessageActionsSheet(
            msg = msg,
            onDismiss = { showActionsFor = null },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("message", msg.body)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                showActionsFor = null
            },
            onRecall = {
                recallingMsg = msg
                showRecallConfirm = true
                showActionsFor = null
            },
            onEdit = {
                editingMsg = msg
                editText = msg.body
                showEditDialog = true
                showActionsFor = null
            },
            onForward = {
                forwardingMsg = msg
                showForwardSheet = true
                showActionsFor = null
            },
            isSelf = msg.direction == 1
        )
    }

    if (showRecallConfirm) {
        AlertDialog(
            onDismissRequest = { showRecallConfirm = false },
            title = { Text("Recall Message") },
            text = { Text("This message will be deleted for everyone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    recallingMsg?.let { msg ->
                        viewModel.recallMessage(
                            msgId = msg.msgId,
                            conversationId = conversationId,
                            recipientUid = recipientUid
                        )
                    }
                    showRecallConfirm = false
                    recallingMsg = null
                }) {
                    Text("Recall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRecallConfirm = false
                    recallingMsg = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Message") },
            text = {
                TextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editingMsg?.let { msg ->
                        viewModel.editMessage(
                            msgId = msg.msgId,
                            newText = editText.trim(),
                            conversationId = conversationId,
                            recipientUid = recipientUid
                        )
                    }
                    showEditDialog = false
                    editingMsg = null
                    editText = ""
                }) {
                    Text("Save", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    editingMsg = null
                    editText = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showForwardSheet) {
        val allConversations by viewModel.conversations.collectAsState()
        ForwardSheet(
            conversations = allConversations,
            onDismiss = {
                showForwardSheet = false
                forwardingMsg = null
            },
            onSelect = { conv ->
                forwardingMsg?.let { msg ->
                    viewModel.forwardMessage(
                        originalBody = msg.body,
                        conversationId = conv.id,
                        recipientUid = "",
                        senderName = conv.name
                    )
                }
                Toast.makeText(context, "Forwarded", Toast.LENGTH_SHORT).show()
                showForwardSheet = false
                forwardingMsg = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    msg: MessageEntity,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onRecall: () -> Unit,
    onEdit: () -> Unit,
    onForward: () -> Unit,
    isSelf: Boolean
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = msg.body,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ActionRow(icon = Icons.Filled.ContentCopy, label = "Copy", onClick = onCopy)
            if (isSelf) {
                ActionRow(icon = Icons.Filled.Edit, label = "Edit", onClick = onEdit)
                ActionRow(icon = Icons.Filled.Delete, label = "Recall", onClick = onRecall, tint = MaterialTheme.colorScheme.error)
            }
            ActionRow(icon = Icons.AutoMirrored.Filled.Forward, label = "Forward", onClick = onForward)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForwardSheet(
    conversations: List<ConversationEntity>,
    onDismiss: () -> Unit,
    onSelect: (ConversationEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Forward to",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(conversations, key = { it.id }) { conv ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = { onSelect(conv) }),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = conv.name.firstOrNull()?.uppercase() ?: "?",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = conv.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: MessageEntity,
    isSelf: Boolean,
    isLastInGroup: Boolean,
    isFirstInGroup: Boolean,
    showSender: Boolean = false,
    senderName: String = "",
    showTranslation: Boolean = false,
    preferredLang: String = "en",
    onLongPress: () -> Unit = {}
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isSelf) {
        if (isDark) SentBubble else SentBubbleLight
    } else {
        if (isDark) ReceivedBubble else ReceivedBubbleLight
    }
    val textColor = if (isDark) DarkOnSurface else LightOnSurface

    var displayText by remember { mutableStateOf(msg.body) }
    var isTranslating by remember { mutableStateOf(false) }
    var hasTranslated by remember { mutableStateOf(false) }
    var translationFailed by remember { mutableStateOf(false) }

    LaunchedEffect(msg.msgId, showTranslation, preferredLang) {
        if (showTranslation && !hasTranslated && !translationFailed) {
            isTranslating = true
            try {
                val result = withContext(Dispatchers.IO) {
                    com.squelch.app.translate.TranslationManager.translateIfNeeded(
                        msg.body, preferredLang
                    )
                }
                if (result.translated != null && result.translated != msg.body) {
                    displayText = result.translated
                } else {
                    displayText = msg.body
                }
                hasTranslated = true
            } catch (e: Exception) {
                displayText = msg.body
                translationFailed = true
            } finally {
                isTranslating = false
            }
        } else if (!showTranslation) {
            displayText = msg.body
            hasTranslated = false
            translationFailed = false
        }
    }

    val shape = when {
        isSelf && isLastInGroup && isFirstInGroup -> RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        isSelf && isLastInGroup -> RoundedCornerShape(18.dp, 4.dp, 4.dp, 18.dp)
        isSelf && isFirstInGroup -> RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        isSelf -> RoundedCornerShape(18.dp, 4.dp, 4.dp, 18.dp)
        isLastInGroup && isFirstInGroup -> RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        isLastInGroup -> RoundedCornerShape(4.dp, 18.dp, 18.dp, 4.dp)
        isFirstInGroup -> RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        else -> RoundedCornerShape(4.dp, 18.dp, 18.dp, 4.dp)
    }

    val verticalPadding = if (isLastInGroup) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isSelf) 48.dp else 6.dp,
                end = if (isSelf) 6.dp else 48.dp,
                top = verticalPadding,
                bottom = verticalPadding
            ),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bgColor)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 4.dp)
        ) {
            Column {
                if (showSender && senderName.isNotEmpty()) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                }
                if (isTranslating) {
                    Text(
                        text = "Translating...",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                    if (showTranslation && hasTranslated && displayText != msg.body) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Original: ${msg.body.take(80)}${if (msg.body.length > 80) "..." else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(msg.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.55f),
                        fontSize = 10.sp
                    )
                    if (isSelf) {
                        Spacer(modifier = Modifier.width(3.dp))
                        DeliveryTick(delivery = msg.delivery)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryTick(delivery: Int) {
    val tickColor = when (delivery) {
        2 -> Accent
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    when (delivery) {
        0 -> Text("\u2713", style = MaterialTheme.typography.labelSmall, color = tickColor, fontSize = 13.sp)
        1 -> Text("\u2713\u2713", style = MaterialTheme.typography.labelSmall, color = tickColor, fontSize = 12.sp)
        2 -> Text("\u2713\u2713", style = MaterialTheme.typography.labelSmall, color = tickColor, fontSize = 12.sp)
    }
}

@Composable
private fun TimestampHeader(timestamp: Long) {
    val label = formatTimestampHeader(timestamp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun shouldShowTimestampHeader(prevMsg: MessageEntity?, currentMsg: MessageEntity): Boolean {
    if (prevMsg == null) return true
    val calPrev = Calendar.getInstance().apply { timeInMillis = prevMsg.timestamp }
    val calCurr = Calendar.getInstance().apply { timeInMillis = currentMsg.timestamp }
    return calPrev.get(Calendar.YEAR) != calCurr.get(Calendar.YEAR) ||
        calPrev.get(Calendar.DAY_OF_YEAR) != calCurr.get(Calendar.DAY_OF_YEAR)
}

private fun formatTimestampHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    if (now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    ) {
        return "TODAY"
    }

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    ) {
        return "YESTERDAY"
    }

    return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp)).uppercase(Locale.getDefault())
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
