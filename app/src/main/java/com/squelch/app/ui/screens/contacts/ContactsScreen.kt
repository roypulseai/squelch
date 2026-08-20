package com.squelch.app.ui.screens.contacts

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
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
import com.squelch.app.ui.theme.OnlineIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contacts: List<ContactEntity> = emptyList(),
    onNavigateToAddContact: () -> Unit = {},
    onNavigateToMyQr: () -> Unit = {},
    onNavigateToUserSearch: () -> Unit = {},
    onContactClick: (ContactEntity) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val sorted = remember(contacts) {
        contacts.sortedBy {
            val name = it.displayName.ifEmpty { it.callsign }
            name.lowercase()
        }
    }

    val filtered = if (searchQuery.isBlank()) sorted
    else sorted.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.callsign.contains(searchQuery, ignoreCase = true)
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
                                    "Search contacts...",
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
                            "Contacts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search contacts",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onNavigateToMyQr) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "My QR",
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
                onClick = onNavigateToAddContact,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { paddingValues ->
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.PersonOutline,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No contacts yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap + to add a contact",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(filtered, key = { it.pubkey }) { contact ->
                    val showDivider = filtered.indexOf(contact) < filtered.lastIndex

                    ContactItem(
                        contact = contact,
                        onClick = { onContactClick(contact) }
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
private fun ContactItem(
    contact: ContactEntity,
    onClick: () -> Unit
) {
    val name = contact.displayName.ifEmpty { contact.callsign }
    val subtitle = when {
        contact.displayName.isNotEmpty() && contact.callsign.isNotEmpty() &&
                contact.callsign != contact.displayName -> contact.callsign
        else -> null
    }
    val isOnline = contact.lastSeen > 0 &&
            (System.currentTimeMillis() - contact.lastSeen) < 5 * 60 * 1000

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            ContactAvatar(
                name = name,
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
            )
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(OnlineIndicator)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ContactAvatar(
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
