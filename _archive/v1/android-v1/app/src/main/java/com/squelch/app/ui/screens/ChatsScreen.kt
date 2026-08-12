package com.squelch.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squelch.app.db.ConversationEntity
import com.squelch.app.db.MessageEntity
import com.squelch.app.mesh.MeshEngine
import com.squelch.app.ui.StatusBar
import com.squelch.app.ui.deliveryGlyph
import com.squelch.app.ui.hhmm
import kotlinx.coroutines.flow.Flow

@Composable
fun ChatsScreen(
    conversations: List<ConversationEntity>,
    meshStatus: MeshEngine.MeshStatus,
    onOpen: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatusBar("MESSAGES")
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "> NO THREADS.\n> TAP A PEER ON THE RADAR.\n> SEND THEM A MESSAGE.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Column
        }
        LazyColumn {
            items(conversations) { conv ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(conv.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (conv.kind == 1) "#" else "@",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = conv.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = hhmm(conv.updatedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    conversation: ConversationEntity,
    messages: Flow<List<MessageEntity>>,
    meshStatus: MeshEngine.MeshStatus,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    val list by messages.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatusBar("${if (conversation.kind == 1) "#" else "@"}${conversation.title}  |  ${hhmm(conversation.updatedAt)}")
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(list) { msg ->
                MessageRow(msg, conversation.kind)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("> ", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "[SEND]",
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    .clickable {
                        if (text.isNotBlank()) {
                            onSend(text)
                            text = ""
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun MessageRow(msg: MessageEntity, convKind: Int) {
    val out = msg.direction == 1
    val sender = when {
        out -> "you"
        convKind == 1 && msg.sender != "me" -> msg.sender.take(6)
        else -> "in"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Text(
            text = "[${hhmm(msg.timestamp)}] $sender: ${msg.body}${if (out) " ${deliveryGlyph(msg.delivery)}" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (out) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
