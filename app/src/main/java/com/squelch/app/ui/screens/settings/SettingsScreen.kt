package com.squelch.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    lockEnabled: Boolean = false,
    isBackingUp: Boolean = false,
    displayName: String = "",
    email: String = "",
    onNavigateToProfile: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onLock: () -> Unit = {},
    onEnableBiometric: () -> Unit = {},
    onDisableBiometric: () -> Unit = {},
    onBackupNow: () -> Unit = {},
    onRestore: () -> Unit = {}
) {
    var checked by remember { mutableStateOf(lockEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ListItem(
                headlineContent = { Text(displayName.ifEmpty { "Profile" }) },
                supportingContent = { Text(email) },
                leadingContent = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToProfile() }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Vault Lock") },
                supportingContent = { Text("Require biometric/PIN to unlock vault") },
                leadingContent = {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = checked,
                        onCheckedChange = { enabled ->
                            checked = enabled
                            if (enabled) onEnableBiometric() else onDisableBiometric()
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Lock Now") },
                leadingContent = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                modifier = Modifier.clickable { onLock() }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Backup to Google Drive") },
                supportingContent = { Text("Save vault & messages to your Drive") },
                leadingContent = {
                    Icon(Icons.Default.Backup, contentDescription = null)
                },
                trailingContent = {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                },
                modifier = Modifier.clickable(enabled = !isBackingUp) { onBackupNow() }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Restore from Google Drive") },
                supportingContent = { Text("Restore contacts & messages from backup") },
                leadingContent = {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                },
                modifier = Modifier.clickable { onRestore() }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Sign Out") },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                },
                modifier = Modifier.clickable { onSignOut() }
            )
        }
    }
}
