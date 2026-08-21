package com.squelch.app.ui.screens.chats

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupOff
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.squelch.app.data.local.entity.GroupMemberEntity
import com.squelch.app.ui.theme.Accent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    groupId: String,
    groupName: String,
    isCreator: Boolean = false,
    onBack: () -> Unit = {},
    onOpenConversation: (groupId: String, groupName: String) -> Unit = { _, _ -> },
    onLeaveGroup: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val group by viewModel.observeGroup(groupId).collectAsState()
    val members by viewModel.getGroupMembers(groupId).collectAsState()
    val selfPub = viewModel.getSelfPubkey()

    val selfMember = members.find { it.edPubHex == selfPub }
    val isAdmin = selfMember?.role == GroupMemberEntity.ROLE_ADMIN

    var editingName by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf(group?.name ?: groupName) }
    var showRemoveDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    nameText = group?.name ?: groupName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (editingName) {
                    Row(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (nameText.isNotBlank()) {
                                viewModel.renameGroup(groupId, nameText.trim())
                            }
                            editingName = false
                        }) {
                            Text("Save", color = Accent)
                        }
                        TextButton(onClick = {
                            nameText = group?.name ?: groupName
                            editingName = false
                        }) {
                            Text("Cancel")
                        }
                    }
                } else {
                    Text(
                        text = group?.name ?: groupName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = isAdmin) {
                            editingName = true
                        }
                    )
                    if (isAdmin) {
                        Text(
                            text = "Tap name to edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${members.size} members",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsRow(
                            icon = if (group?.muted == true) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                            label = if (group?.muted == true) "Unmute" else "Mute",
                            subtitle = if (group?.muted == true) "Notifications off" else "Notifications on",
                            onClick = { viewModel.toggleMuteGroup(groupId, group?.muted != true) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        SettingsRow(
                            icon = Icons.Filled.GroupOff,
                            label = "Leave group",
                            subtitle = "You will no longer receive messages",
                            onClick = { showLeaveDialog = true },
                            tint = MaterialTheme.colorScheme.error
                        )
                        if (isAdmin) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            SettingsRow(
                                icon = Icons.Filled.Add,
                                label = "Add member",
                                subtitle = "Invite contacts to this group",
                                onClick = { showAddMemberDialog = true },
                                tint = Accent
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Members",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${members.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(members, key = { it.edPubHex }) { member ->
                val isSelf = member.edPubHex == selfPub
                val memberDisplayName = member.displayName.ifEmpty { member.edPubHex.take(8) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = memberDisplayName.firstOrNull()?.uppercase() ?: "?",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSelf) "$memberDisplayName (You)" else memberDisplayName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (member.role == GroupMemberEntity.ROLE_ADMIN) {
                                Text(
                                    text = "Admin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (isAdmin && !isSelf) {
                            IconButton(
                                onClick = {
                                    if (member.role == GroupMemberEntity.ROLE_ADMIN) {
                                        viewModel.demoteFromAdmin(groupId, member.edPubHex)
                                    } else {
                                        viewModel.promoteToAdmin(groupId, member.edPubHex)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (member.role == GroupMemberEntity.ROLE_ADMIN)
                                        Icons.Filled.Star else Icons.Filled.StarOutline,
                                    contentDescription = "Toggle admin",
                                    tint = if (member.role == GroupMemberEntity.ROLE_ADMIN)
                                        Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    showRemoveDialog = Pair(member.edPubHex, memberDisplayName)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.RemoveCircle,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Group") },
            text = { Text("You will no longer receive messages from this group. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveGroup(groupId)
                    showLeaveDialog = false
                    onLeaveGroup()
                }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    showRemoveDialog?.let { (pubKey, name) ->
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text("Remove Member") },
            text = { Text("Remove $name from this group?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMemberFromGroup(groupId, pubKey)
                    showRemoveDialog = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            groupId = groupId,
            currentMembers = members.map { it.edPubHex },
            viewModel = viewModel,
            onDismiss = { showAddMemberDialog = false },
            onMemberAdded = { showAddMemberDialog = false }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberDialog(
    groupId: String,
    currentMembers: List<String>,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onMemberAdded: () -> Unit
) {
    val contacts by viewModel.contacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val available = contacts.filter { c ->
        c.pubkey !in currentMembers &&
            (c.displayName.contains(searchQuery, ignoreCase = true) ||
                c.callsign.contains(searchQuery, ignoreCase = true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Member") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contacts...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(available, key = { it.pubkey }) { contact ->
                        val displayName = contact.callsign.ifEmpty { contact.displayName.ifEmpty { contact.pubkey.take(8) } }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addMemberToGroup(groupId, contact.pubkey, displayName)
                                    onMemberAdded()
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = displayName.firstOrNull()?.uppercase() ?: "?",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (available.isEmpty() && searchQuery.isNotEmpty()) {
                        item {
                            Text(
                                text = "No matching contacts",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
