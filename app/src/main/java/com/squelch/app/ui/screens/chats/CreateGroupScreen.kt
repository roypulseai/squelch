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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.data.local.entity.ContactEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    contacts: List<ContactEntity>,
    onBack: () -> Unit,
    onGroupCreated: (name: String, memberPubKeys: List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var selectedPubKeys by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
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
        },
        floatingActionButton = {
            if (groupName.isNotBlank() && selectedPubKeys.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onGroupCreated(groupName.trim(), selectedPubKeys.toList()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Create Group")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                label = { Text("Group name") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            if (selectedPubKeys.isNotEmpty()) {
                Text(
                    text = "${selectedPubKeys.size} member${if (selectedPubKeys.size > 1) "s" else ""} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "Select members",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts, key = { it.pubkey }) { contact ->
                    val isSelected = selectedPubKeys.contains(contact.pubkey)
                    val name = contact.displayName.ifEmpty { contact.callsign }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPubKeys = if (isSelected) {
                                    selectedPubKeys - contact.pubkey
                                } else {
                                    selectedPubKeys + contact.pubkey
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(avatarColor(name)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (contact.callsign.isNotEmpty() && contact.callsign != contact.displayName) {
                                Text(
                                    text = contact.callsign,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
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
