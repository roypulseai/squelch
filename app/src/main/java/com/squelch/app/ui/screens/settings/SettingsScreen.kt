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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.delay

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
    isPausedModels: Boolean = false,
    modelStatusText: String = "",
    modelFailedCount: Int = 0,
    modelCurrentModel: String = "",
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
    onToggleTranslation: () -> Unit = {},
    onPauseModelDownload: () -> Unit = {},
    onRefreshModelDownload: () -> Unit = {}
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
            if (isDownloadingModels || isPausedModels || modelDownloadProgress > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = when {
                                isDownloadingModels -> MaterialTheme.colorScheme.primary
                                isPausedModels -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = modelStatusText.ifEmpty {
                                    if (isDownloadingModels) "Downloading translation models..."
                                    else if (isPausedModels) "Download paused"
                                    else "Translation models ready"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (modelFailedCount > 0) {
                                Text(
                                    text = "$modelFailedCount model${if (modelFailedCount != 1) "s" else ""} failed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { modelDownloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = when {
                                    isPausedModels -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isDownloadingModels) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = onPauseModelDownload
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Pause",
                                    modifier = Modifier.padding(7.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (isPausedModels) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                onClick = onPauseModelDownload
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Resume",
                                    modifier = Modifier.padding(7.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (!isDownloadingModels && modelFailedCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = onRefreshModelDownload
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    modifier = Modifier.padding(7.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
        val languageFlags = mapOf(
            "af" to "\uD83C\uDDE6\uD83C\uDDFF", "ar" to "\uD83C\uDDF8\uD83C\uDDE6", "be" to "\uD83C\uDDE7\uD83C\uDDFE",
            "bg" to "\uD83C\uDDE7\uD83C\uDDEC", "bn" to "\uD83C\uDDEE\uD83C\uDDF3", "ca" to "\uD83C\uDDEA\uD83C\uDDF8",
            "cs" to "\uD83C\uDDE8\uD83C\uDDFF", "cy" to "\uD83C\uDFF4\uD83C\uDF3D", "da" to "\uD83C\uDDE9\uD83C\uDDF0",
            "de" to "\uD83C\uDDE9\uD83C\uDDEA", "el" to "\uD83C\uDDEC\uD83C\uDDF7", "en" to "\uD83C\uDDEC\uD83C\uDDE7",
            "eo" to "\u2B50", "es" to "\uD83C\uDDEA\uD83C\uDDF8", "et" to "\uD83C\uDDEA\uD83C\uDDEA",
            "fa" to "\uD83C\uDDEE\uD83C\uDDF7", "fi" to "\uD83C\uDDEB\uD83C\uDDEE", "fr" to "\uD83C\uDDEB\uD83C\uDDF7",
            "ga" to "\uD83C\uDDEE\uD83C\uDDEA", "gl" to "\uD83C\uDDEA\uD83C\uDDF8", "gu" to "\uD83C\uDDEE\uD83C\uDDF3",
            "he" to "\uD83C\uDDF1\uD83C\uDDEE", "hi" to "\uD83C\uDDEE\uD83C\uDDF3", "hr" to "\uD83C\uDDED\uD83C\uDDF7",
            "ht" to "\uD83C\uDDED\uD83C\uDDF9", "hu" to "\uD83C\uDDED\uD83C\uDDFA", "id" to "\uD83C\uDDEE\uD83C\uDDE9",
            "is" to "\uD83C\uDDEE\uD83C\uDDF8", "it" to "\uD83C\uDDEE\uD83C\uDDF9", "ja" to "\uD83C\uDDEF\uD83C\uDDF5",
            "ka" to "\uD83C\uDDEC\uD83C\uDDEA", "kn" to "\uD83C\uDDEE\uD83C\uDDF3", "ko" to "\uD83C\uDDF0\uD83C\uDDF7",
            "lt" to "\uD83C\uDDF1\uD83C\uDDF9", "lv" to "\uD83C\uDDF1\uD83C\uDDFB", "mk" to "\uD83C\uDDF2\uD83C\uDDF0",
            "mr" to "\uD83C\uDDEE\uD83C\uDDF3", "ms" to "\uD83C\uDDF2\uD83C\uDDFE", "mt" to "\uD83C\uDDF2\uD83C\uDDF9",
            "nl" to "\uD83C\uDDF3\uD83C\uDDF1", "no" to "\uD83C\uDDF3\uD83C\uDDF4", "pl" to "\uD83C\uDDF5\uD83C\uDDF1",
            "pt" to "\uD83C\uDDF5\uD83C\uDDF9", "ro" to "\uD83C\uDDF7\uD83C\uDDF4", "ru" to "\uD83C\uDDF7\uD83C\uDDFA",
            "sk" to "\uD83C\uDDF8\uD83C\uDDF0", "sl" to "\uD83C\uDDF8\uD83C\uDDEE", "sq" to "\uD83C\uDDE6\uD83C\uDDF1",
            "sv" to "\uD83C\uDDF8\uD83C\uDDEA", "sw" to "\uD83C\uDDF0\uD83C\uDDEA", "ta" to "\uD83C\uDDEE\uD83C\uDDF3",
            "te" to "\uD83C\uDDEE\uD83C\uDDF3", "th" to "\uD83C\uDDF9\uD83C\uDDED", "tl" to "\uD83C\uDDF5\uD83C\uDDED",
            "tr" to "\uD83C\uDDF9\uD83C\uDDF7", "uk" to "\uD83C\uDDFA\uD83C\uDDE6", "ur" to "\uD83C\uDDF5\uD83C\uDDF0",
            "vi" to "\uD83C\uDDFB\uD83C\uDDF3", "zh" to "\uD83C\uDDE8\uD83C\uDDF3"
        )

        var searchQuery by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = {
                showLanguagePicker = false
                searchQuery = ""
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Preferred Language",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Messages will be translated to this language",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search languages...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        ) {
            val filteredLanguages = languageNames.entries
                .sortedBy { it.value }
                .filter { (code, name) ->
                    searchQuery.isEmpty() ||
                        name.contains(searchQuery, ignoreCase = true) ||
                        code.contains(searchQuery, ignoreCase = true)
                }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(bottom = 16.dp)
            ) {
                items(filteredLanguages, key = { it.key }) { (code, name) ->
                    val isSelected = code == preferredLanguage
                    val flag = languageFlags[code] ?: "\uD83C\uDF0D"
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSetPreferredLanguage(code)
                                showLanguagePicker = false
                                searchQuery = ""
                            },
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = flag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = code.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
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
