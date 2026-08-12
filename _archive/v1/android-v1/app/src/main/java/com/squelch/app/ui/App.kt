package com.squelch.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.squelch.app.ui.screens.ChatScreen
import com.squelch.app.ui.screens.ChatsScreen
import com.squelch.app.ui.screens.ContactsScreen
import com.squelch.app.ui.screens.RadarScreen
import com.squelch.app.ui.screens.RoomsScreen
import com.squelch.app.ui.screens.SettingsScreen

private val PERMISSIONS = buildList {
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.POST_NOTIFICATIONS)
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}

@Composable
fun SquelchRoot(vm: SquelchViewModel = viewModel()) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(granted) {
        if (granted) vm.startMesh()
    }

    if (!granted) {
        PermissionGate(onGrant = { launcher.launch(PERMISSIONS.toTypedArray()) })
    } else {
        MainScaffold(vm)
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SQUELCH",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "NEEDS BLUETOOTH + LOCATION + NOTIFICATIONS\nTO OPEN THE MESH.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "[GRANT]",
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .clickable { onGrant() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun MainScaffold(vm: SquelchViewModel) {
    val nav = rememberNavController()
    val meshStatus by vm.meshStatus.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val rooms by vm.rooms.collectAsState()
    val linkedPeers = remember(meshStatus) {
        vm.engine.peers.all.filter { it.link != null }.map { it.edHex }.toSet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val routes = listOf("radar" to "*", "chats" to ">", "rooms" to "#", "contacts" to "@", "settings" to "=")
                routes.forEach { (route, glyph) ->
                    NavigationBarItem(
                        selected = nav.currentBackStackEntryAsState().value?.destination?.route == route,
                        onClick = {
                            nav.navigate(route) {
                                popUpTo("radar") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Text(glyph, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                        },
                        label = {
                            Text(route.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            NavHost(nav, startDestination = "radar") {
                composable("radar") {
                    RadarScreen(
                        meshStatus = meshStatus,
                        myCallsign = vm.myCallsign,
                        myFingerprint = vm.myFingerprint,
                        contacts = contacts,
                        linkedPeers = linkedPeers
                    )
                }
                composable("chats") {
                    ChatsScreen(conversations = conversations, meshStatus = meshStatus) { id ->
                        nav.navigate("chat/$id")
                    }
                }
                composable(
                    "chat/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    val conv = conversations.firstOrNull { it.id == id }
                    if (conv != null) {
                        ChatScreen(
                            conversation = conv,
                            messages = vm.messagesFor(id),
                            meshStatus = meshStatus,
                            onSend = { text ->
                                if (conv.kind == 1) vm.sendRoomMessage(id, text) else vm.sendDm(id, text)
                            },
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
                composable("rooms") {
                    RoomsScreen(
                        rooms = rooms,
                        meshStatus = meshStatus,
                        onJoin = { name, pass -> vm.joinRoom(name, pass) },
                        onLeave = { id -> vm.leaveRoom(id) },
                        onOpen = { id -> nav.navigate("chat/$id") }
                    )
                }
                composable("contacts") {
                    ContactsScreen(
                        contacts = contacts,
                        meshStatus = meshStatus,
                        linkedPeers = linkedPeers,
                        onIdentityRead = { ed, x, nonce -> vm.onNfcIdentityRead(ed, x, nonce) }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        meshStatus = meshStatus,
                        myCallsign = vm.myCallsign,
                        myFingerprint = vm.myFingerprint,
                        meshRunning = vm.meshRunning(),
                        onToggleMesh = { vm.toggleMesh() },
                        onExport = { vm.exportIdentity() },
                        onImport = { vm.importIdentity(it) },
                        onPurge = { hours -> vm.purgeOlderThan(hours) }
                    )
                }
            }
        }
    }
}
