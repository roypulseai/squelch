package com.squelch.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object SignIn : Screen("sign_in")
    data object Unlock : Screen("unlock")
    data object Chats : Screen("chats")
    data object Conversation : Screen("conversation/{conversationId}/{conversationName}/{isGroup}") {
        fun createRoute(conversationId: String, conversationName: String, isGroup: Boolean = false): String =
            "conversation/$conversationId/${java.net.URLEncoder.encode(conversationName, "UTF-8")}/$isGroup"
    }
    data object Contacts : Screen("contacts")
    data object AddContact : Screen("add_contact")
    data object MyQr : Screen("my_qr")
    data object UserSearch : Screen("user_search")
    data object NewChat : Screen("new_chat")
    data object NewGroup : Screen("new_group")
    data object Radar : Screen("radar")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
    data object Restore : Screen("restore")
    data object Permissions : Screen("permissions")
    data object Strangers : Screen("strangers")
    data object GroupInfo : Screen("group_info/{groupId}/{groupName}") {
        fun createRoute(groupId: String, groupName: String): String =
            "group_info/$groupId/${java.net.URLEncoder.encode(groupName, "UTF-8")}"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Chats, "Chats", Icons.AutoMirrored.Filled.Chat),
    BottomNavItem(Screen.Contacts, "Contacts", Icons.Default.Contacts),
    BottomNavItem(Screen.Radar, "Radar", Icons.Default.Radar),
    BottomNavItem(Screen.Settings, "Settings", Icons.Default.Settings)
)
