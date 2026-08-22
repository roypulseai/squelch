package com.squelch.app.ui.screens.settings

import com.squelch.app.BuildConfig
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    lockEnabled: Boolean = false,
    isBackingUp: Boolean = false,
    displayName: String = "",
    email: String = "",
    preferredLanguage: String = "en",
    showTranslation: Boolean = false,
    modelDownloadProgress: Float = 0f,
    isDownloadingModels: Boolean = false,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToBlockedUsers: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onLock: () -> Unit = {},
    onEnableBiometric: () -> Unit = {},
    onDisableBiometric: () -> Unit = {},
    onBackupNow: () -> Unit = {},
    onRestore: () -> Unit = {},
    onSetPreferredLanguage: (String) -> Unit = {},
    onToggleTranslation: () -> Unit = {}
) {
    var checked by remember { mutableStateOf(lockEnabled) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val languageNames = mapOf(
        "af" to "Afrikaans", "ar" to "Arabic", "be" to "Belarusian", "bg" to "Bulgarian",
        "bn" to "Bengali", "ca" to "Catalan", "cs" to "Czech", "cy" to "Welsh",
        "da" to "Danish", "de" to "German", "el" to "Greek", "en" to "English",
        "eo" to "Esperanto", "es" to "Spanish", "et" to "Estonian", "fa" to "Persian",
        "fi" to "Finnish", "fr" to "French", "ga" to "Irish", "gl" to "Galician",
        "gu" to "Gujarati", "he" to "Hebrew", "hi" to "Hindi", "hr" to "Croatian",
        "ht" to "Haitian Creole", "hu" to "Hungarian", "id" to "Indonesian", "is" to "Icelandic",
        "it" to "Italian", "ja" to "Japanese", "ka" to "Georgian", "kn" to "Kannada",
        "ko" to "Korean", "lt" to "Lithuanian", "lv" to "Latvian", "mk" to "Macedonian",
        "mr" to "Marathi", "ms" to "Malay", "mt" to "Maltese", "nl" to "Dutch",
        "no" to "Norwegian", "pl" to "Polish", "pt" to "Portuguese", "ro" to "Romanian",
        "ru" to "Russian", "sk" to "Slovak", "sl" to "Slovenian", "sq" to "Albanian",
        "sv" to "Swedish", "sw" to "Swahili", "ta" to "Tamil", "te" to "Telugu",
        "th" to "Thai", "tl" to "Tagalog", "tr" to "Turkish", "uk" to "Ukrainian",
        "ur" to "Urdu", "vi" to "Vietnamese", "zh" to "Chinese"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
        ) {
            ProfileCard(
                displayName = displayName,
                email = email,
                onClick = onNavigateToProfile
            )

            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("Account")
            SettingsItem(
                icon = Icons.Default.Person,
                title = "Profile",
                onClick = onNavigateToProfile
            )

            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("Security")
            SettingsItem(
                icon = Icons.Default.Fingerprint,
                title = "Vault Lock",
                subtitle = "Require biometric/PIN to unlock vault",
                trailing = {
                    Switch(
                        checked = checked,
                        onCheckedChange = { enabled ->
                            checked = enabled
                            if (enabled) onEnableBiometric() else onDisableBiometric()
                        }
                    )
                }
            )
            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Lock Now",
                onClick = onLock
            )
            SettingsItem(
                icon = Icons.Default.Block,
                title = "Blocked Users",
                subtitle = "Manage blocked contacts",
                onClick = onNavigateToBlockedUsers
            )
            SettingsItem(
                icon = Icons.Default.Shield,
                title = "Permissions",
                subtitle = "Manage app permissions",
                onClick = onNavigateToPermissions
            )

            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("Translation")
            SettingsItem(
                icon = Icons.Default.Translate,
                title = "Translate Messages",
                subtitle = "Automatically translate received messages",
                trailing = {
                    Switch(
                        checked = showTranslation,
                        onCheckedChange = { onToggleTranslation() }
                    )
                }
            )
            SettingsItem(
                icon = Icons.Default.Translate,
                title = "Preferred Language",
                subtitle = languageNames[preferredLanguage] ?: preferredLanguage,
                onClick = { showLanguagePicker = true }
            )
            if (isDownloadingModels || modelDownloadProgress > 0f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isDownloadingModels) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDownloadingModels) "Downloading translation models..." else "Translation models ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { modelDownloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("Backup & Restore")
            SettingsItem(
                icon = Icons.Default.Backup,
                title = "Backup to Google Drive",
                subtitle = "Save your encrypted vault & message history",
                trailing = {
                    if (isBackingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                onClick = { if (!isBackingUp) onBackupNow() }
            )
            SettingsItem(
                icon = Icons.Default.CloudDownload,
                title = "Restore from Google Drive",
                subtitle = "Restore contacts & messages on a new device",
                onClick = onRestore
            )

            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("About")
            Text(
                text = "Squelch v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            SettingsItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Sign Out",
                titleColor = MaterialTheme.colorScheme.error,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = onSignOut
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text("Preferred Language") },
            text = {
                Column {
                    Text(
                        text = "Messages will be translated to this language",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val sortedLanguages = languageNames.entries.sortedBy { it.value }
                    sortedLanguages.forEach { (code, name) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clickable {
                                    onSetPreferredLanguage(code)
                                    showLanguagePicker = false
                                },
                            color = if (code == preferredLanguage)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (code == preferredLanguage) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Spacer(modifier = Modifier.width(26.dp))
                                }
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    displayName: String,
    email: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val initials = displayName
                .split(" ")
                .take(2)
                .joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
            if (initials.isNotEmpty()) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName.ifEmpty { "Your Name" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (email.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(if (subtitle != null) 64.dp else 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}
