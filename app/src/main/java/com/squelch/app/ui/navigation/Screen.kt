package com.squelch.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object SignIn : Screen("sign_in")
    data object PinSetup : Screen("pin_setup")
    data object PinUnlock : Screen("pin_unlock")
    data object MnemonicBackup : Screen("mnemonic_backup/{pin}") {
        fun createRoute(pin: String) = "mnemonic_backup/$pin"
    }
    data object Chats : Screen("chats")
    data object Contacts : Screen("contacts")
    data object AddContact : Screen("add_contact")
    data object MyQr : Screen("my_qr")
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
