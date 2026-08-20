package com.squelch.app.ui.screens.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.data.local.entity.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onNavigateToConversation: (id: String, name: String) -> Unit = { _, _ -> },
    onNavigateToNewChat: () -> Unit = {},
    onNavigateToStrangers: () -> Unit = {},
    strangerCount: Int = 0,
    viewModel: ChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredConversations = if (searchQuery.isBlank()) conversations
    else conversations.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Search...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "Chats",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    },
                    actions = {
                        if (strangerCount > 0) {
                            IconButton(onClick = onNavigateToStrangers) {
                                Box {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Unknown contacts",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = strangerCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search chats",
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToNewChat,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { paddingValues ->
        if (filteredConversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isSearchActive) "No results found" else "Start a new conversation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isSearchActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the button below to begin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            val sorted = filteredConversations.sortedWith(
                compareByDescending<ConversationEntity> { it.pinned }
                    .thenByDescending { it.lastMessageTimestamp }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(sorted, key = { it.id }) { conv ->
                    val showDivider = sorted.indexOf(conv) < sorted.lastIndex

                    ConversationItem(
                        conversation = conv,
                        onClick = { onNavigateToConversation(conv.id, conv.name) }
                    )

                    if (showDivider) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    val hasUnread = conversation.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            name = conversation.name,
            modifier = Modifier.size(52.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.pinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (conversation.muted) {
                        @Suppress("DEPRECATION")
                        Icon(
                            Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = formatChatTime(conversation.lastMessageTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessagePreview.ifEmpty { "No messages yet" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (hasUnread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UnreadBadge(count = conversation.unreadCount)
                }
            }
        }
    }
}

@Composable
private fun Avatar(
    name: String,
    modifier: Modifier = Modifier
) {
    val color = avatarColor(name)
    val initial = name.firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun avatarColor(name: String): Color {
    val colors = listOf(
        Color(0xFFE17076),
        Color(0xFF7BC862),
        Color(0xFFE5C357),
        Color(0xFF65AADD),
        Color(0xFFA695E7),
        Color(0xFFEE7AAE),
        Color(0xFF6EC9CB),
        Color(0xFFFAA774),
    )
    val hash = name.fold(0) { acc, c -> acc + c.code }
    return colors[hash % colors.size]
}

private fun formatChatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 604_800_000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(timestamp))
    }
}
