package com.squelch.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object SignIn : Screen("sign_in")
    data object BiometricSetup : Screen("biometric_setup")
    data object BiometricUnlock : Screen("biometric_unlock")
    data object MnemonicBackup : Screen("mnemonic_backup/{mnemonic}") {
        fun createRoute(mnemonic: String) = "mnemonic_backup/${java.net.URLEncoder.encode(mnemonic, "UTF-8")}"
    }
    data object MnemonicRecovery : Screen("mnemonic_recovery")
    data object Chats : Screen("chats")
    data object Conversation : Screen("conversation/{conversationId}/{conversationName}") {
        fun createRoute(conversationId: String, conversationName: String): String =
            "conversation/$conversationId/${java.net.URLEncoder.encode(conversationName, "UTF-8")}"
    }
    data object Contacts : Screen("contacts")
    data object AddContact : Screen("add_contact")
    data object MyQr : Screen("my_qr")
    data object NewChat : Screen("new_chat")
    data object Radar : Screen("radar")
    data object Settings : Screen("settings")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Chats, "Chats", Icons.Default.Chat),
    BottomNavItem(Screen.Contacts, "Contacts", Icons.Default.Contacts),
    BottomNavItem(Screen.Radar, "Radar", Icons.Default.Radar),
    BottomNavItem(Screen.Settings, "Settings", Icons.Default.Settings)
)
