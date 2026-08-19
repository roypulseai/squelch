package com.squelch.app.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.squelch.app.auth.BiometricVaultManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    biometricVaultManager: BiometricVaultManager? = null,
    onSignOut: () -> Unit = {},
    onLock: () -> Unit = {},
    onEnableBiometric: () -> Unit = {},
    onDisableBiometric: () -> Unit = {}
) {
    val lockEnabled = biometricVaultManager?.isLockEnabled() ?: false

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
                headlineContent = { Text("Vault Lock") },
                supportingContent = { Text("Require biometric/PIN to unlock vault") },
                leadingContent = {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = lockEnabled,
                        onCheckedChange = { enabled ->
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
                headlineContent = { Text("Sign Out") },
                leadingContent = {
                    Icon(Icons.Default.Logout, contentDescription = null)
                },
                modifier = Modifier.clickable { onSignOut() }
            )
        }
    }
}
