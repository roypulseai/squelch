package com.squelch.app.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.squelch.app.data.local.entity.MessageEntity
import com.squelch.app.ui.theme.Accent
import com.squelch.app.ui.theme.SentBubble
import com.squelch.app.ui.theme.ReceivedBubble
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    conversationName: String,
    selfPubkey: String,
    recipientUid: String = "",
    networkType: String = "Internet",
    isGroup: Boolean = false,
    onBack: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages(conversationId).collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.clearUnread(conversationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                text = "online",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
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
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(messages, key = { _, msg -> msg.msgId }) { index, msg ->
                    val isSelf = msg.direction == 1
                    val prevMsg = messages.getOrNull(index - 1)
                    val nextMsg = messages.getOrNull(index + 1)

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
                        showSender = isGroup && !isSelf
                    )
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
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
                            value = inputText,
                            onValueChange = { inputText = it },
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
                                inputText = ""
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
}

@Composable
private fun MessageBubble(
    msg: MessageEntity,
    isSelf: Boolean,
    isLastInGroup: Boolean,
    isFirstInGroup: Boolean,
    showSender: Boolean = false
) {
    val bgColor = if (isSelf) SentBubble else ReceivedBubble
    val textColor = MaterialTheme.colorScheme.onSurface

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
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 4.dp)
        ) {
            Column {
                if (showSender) {
                    Text(
                        text = msg.sender.take(8),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                }
                Text(
                    text = msg.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
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
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    val tickText = when (delivery) {
        0 -> "\u2713"
        1 -> "\u2713\u2713"
        2 -> "\u2713\u2713"
        else -> ""
    }
    Text(
        text = tickText,
        style = MaterialTheme.typography.labelSmall,
        color = tickColor,
        fontSize = 11.sp
    )
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
