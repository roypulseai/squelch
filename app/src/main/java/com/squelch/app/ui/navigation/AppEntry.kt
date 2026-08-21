package com.squelch.app.ui.navigation

import android.app.Activity
import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.squelch.app.auth.AuthRepository
import com.squelch.app.auth.AuthState
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.VaultSession
import com.squelch.app.data.local.entity.ContactEntity
import com.squelch.app.data.remote.DriveBackupManager
import com.squelch.app.data.remote.FirestoreVaultManager
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.data.repository.VaultRepository.VaultState
import com.squelch.app.messaging.MessageForegroundService
import com.squelch.app.mesh.engine.MeshEngineManager
import com.squelch.app.mesh.relay.MessageRelay
import com.squelch.app.qr.QrContact
import com.squelch.app.ui.screens.chats.ChatsScreen
import com.squelch.app.ui.screens.chats.ConversationScreen
import com.squelch.app.ui.screens.chats.CreateGroupScreen
import com.squelch.app.ui.screens.chats.GroupInfoScreen
import com.squelch.app.ui.screens.chats.NewChatScreen
import com.squelch.app.ui.screens.chats.StrangerMessagesScreen
import com.squelch.app.ui.screens.chats.BlockedUsersScreen
import com.squelch.app.ui.screens.chats.ChatViewModel
import com.squelch.app.ui.screens.contacts.AddContactScreen
import com.squelch.app.ui.screens.contacts.ContactsScreen
import com.squelch.app.ui.screens.contacts.MyQrScreen
import com.squelch.app.ui.screens.contacts.UserSearchScreen
import com.squelch.app.ui.screens.mesh.RadarScreen
import com.squelch.app.ui.screens.mesh.RadarViewModel
import com.squelch.app.ui.screens.onboarding.BiometricGateScreen
import com.squelch.app.ui.screens.onboarding.PermissionsScreen
import com.squelch.app.ui.screens.onboarding.hasPermissionsAsked
import com.squelch.app.ui.screens.onboarding.RestoreScreen
import com.squelch.app.ui.screens.onboarding.SignInScreen
import com.squelch.app.ui.screens.settings.SettingsScreen
import com.squelch.app.ui.screens.settings.ProfileScreen
import com.squelch.app.util.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private val bottomNavRoutes = setOf(
    Screen.Chats.route,
    Screen.Contacts.route,
    Screen.Radar.route,
    Screen.Settings.route
)

@Composable
fun AppEntry(
    authRepository: AuthRepository,
    vaultRepository: VaultRepository,
    driveBackupManager: DriveBackupManager,
    firestoreVaultManager: FirestoreVaultManager,
    messageRelay: MessageRelay,
    meshEngineManager: MeshEngineManager
) {
    val navController = rememberNavController()
    val authState by authRepository.state.collectAsState()
    val vaultState by vaultRepository.state.collectAsState()
    val activity = LocalContext.current as FragmentActivity

    val isSignedIn = authState is AuthState.SignedIn

    var showRestore by remember { mutableStateOf(false) }
    var restoreChecking by remember { mutableStateOf(true) }
    var isBackingUp by remember { mutableStateOf(false) }
    var driveSignInData by remember { mutableStateOf<android.content.Intent?>(null) }

    val driveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val success = driveBackupManager.onDriveSignInResult(result.data)
            if (success) {
                isBackingUp = true
                MainScope().launch {
                    performBackup(driveBackupManager, firestoreVaultManager, authRepository)
                    isBackingUp = false
                }
            }
        }
    }

    val startDestination = when {
        !isSignedIn -> Screen.SignIn.route
        vaultState is VaultState.Unlocked -> {
            if (!hasPermissionsAsked(activity)) Screen.Permissions.route
            else Screen.Chats.route
        }
        vaultState is VaultState.BiometricRequired -> Screen.Unlock.route
        vaultState is VaultState.Error -> Screen.Unlock.route
        else -> Screen.SignIn.route
    }

    LaunchedEffect(isSignedIn) {
        if (isSignedIn && vaultState is VaultState.Idle) {
            vaultRepository.checkVaultState()
        }
    }

    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            writeUserProfile(authRepository)
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    com.squelch.app.messaging.FcmTokenManager.registerToken(token)
                }
        }
    }

    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            restoreChecking = true
            val hasBackup = try {
                driveBackupManager.ensureDriveAccess() && driveBackupManager.hasBackup()
            } catch (_: Exception) {
                false
            }
            showRestore = hasBackup
            restoreChecking = false
        }
    }

    LaunchedEffect(vaultState) {
        when (vaultState) {
            is VaultState.Unlocked -> {
                val signed = authRepository.signedIn()
                val db = vaultRepository.db
                if (signed != null && db != null) {
                    val identity = Identity.fromGoogleUid(signed.googleUid)
                    val edPubHex = identity.edPub.toHex()
                    if (!messageRelay.isRunning) {
                        messageRelay.start(edPubHex, db, identity, signed.email)
                    }
                    meshEngineManager.getOrCreate()
                    if (!MessageForegroundService.isRunning) {
                        android.content.Intent(activity, MessageForegroundService::class.java)
                            .putExtra("edPubHex", edPubHex)
                            .also { activity.startForegroundService(it) }
                    }
                }
                val current = navController.currentDestination?.route
                if (current == Screen.SignIn.route || current == Screen.Unlock.route || current == Screen.Permissions.route) {
                    if (showRestore) {
                        navController.navigate(Screen.Restore.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else if (!hasPermissionsAsked(activity)) {
                        navController.navigate(Screen.Permissions.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Chats.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            is VaultState.BiometricRequired -> {
                val current = navController.currentDestination?.route
                if (current != Screen.Unlock.route && current != Screen.SignIn.route) {
                    navController.navigate(Screen.Unlock.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {
                if (messageRelay.isRunning) {
                    messageRelay.stop()
                }
                meshEngineManager.stop()
                if (MessageForegroundService.isRunning) {
                    MessageForegroundService.stop(activity)
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes

    val contactsFlow = remember(vaultState) {
        vaultRepository.db?.contacts()?.observeAll()
    }
    val contacts by contactsFlow
        ?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                selected = currentRoute == item.screen.route,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) }
            ) {
                composable(Screen.SignIn.route) {
                    SignInScreen(
                        authRepository = authRepository,
                        onSignedIn = {
                            navController.navigate(Screen.Permissions.route) {
                                popUpTo(Screen.SignIn.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Unlock.route) {
                    val vs by vaultRepository.state.collectAsState()
                    BiometricGateScreen(
                        loading = vs is VaultState.Loading,
                        errorMessage = (vs as? VaultState.Error)?.message,
                        onAuthenticate = {
                            vaultRepository.unlockWithBiometric(activity)
                        },
                        onUseRecoveryPhrase = null
                    )
                }

                composable(Screen.Restore.route) {
                    var restoring by remember { mutableStateOf(false) }
                    var restoreError by remember { mutableStateOf<String?>(null) }

                    RestoreScreen(
                        isLoading = restoring,
                        errorMessage = restoreError,
                        onRestore = {
                            restoring = true
                            restoreError = null
                            MainScope().launch {
                                try {
                                    val vaultBlob = driveBackupManager.restoreVault()
                                    if (vaultBlob != null) {
                                        val signed = authRepository.signedIn()
                                        if (signed != null) {
                                            firestoreVaultManager.uploadVault(signed.googleUid, vaultBlob)
                                        }
                                    }
                                    val dbFile = driveBackupManager.restoreMessages()
                                    if (dbFile != null) {
                                        val target = activity.getDatabasePath("squelch.db")
                                        dbFile.copyTo(target, overwrite = true)
                                    }
                                    vaultRepository.checkVaultState()
                                    restoring = false
                                } catch (e: Exception) {
                                    restoreError = "Restore failed: ${e.message}"
                                    restoring = false
                                }
                            }
                        },
                        onSkip = {
                            navController.navigate(Screen.Chats.route) {
                                popUpTo(Screen.Restore.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Permissions.route) {
                    PermissionsScreen(
                        onAllGranted = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Chats.route) {
                                    popUpTo(Screen.Permissions.route) { inclusive = true }
                                }
                            }
                        },
                        onSkip = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Chats.route) {
                                    popUpTo(Screen.Permissions.route) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable(Screen.Chats.route) {
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    val strangerList by chatViewModel.strangers.collectAsState()
                    ChatsScreen(
                        onNavigateToConversation = { id, name ->
                            navController.navigate(Screen.Conversation.createRoute(id, name, false))
                        },
                        onNavigateToNewChat = {
                            navController.navigate(Screen.NewChat.route)
                        },
                        onNavigateToStrangers = {
                            navController.navigate(Screen.Strangers.route)
                        },
                        strangerCount = strangerList.size,
                        onDeleteConversation = { id -> chatViewModel.deleteConversation(id) },
                        onTogglePin = { id -> chatViewModel.togglePin(id) },
                        onToggleMute = { id -> chatViewModel.toggleMute(id) },
                        viewModel = chatViewModel
                    )
                }

                composable(Screen.NewChat.route) {
                    NewChatScreen(
                        database = vaultRepository.db,
                        onBack = { navController.popBackStack() },
                        onContactSelected = { contact ->
                            val convId = contact.pubkey
                            val convName = contact.callsign.ifEmpty { contact.displayName }
                            MainScope().launch {
                                withContext(Dispatchers.IO) {
                                    val db = vaultRepository.db ?: return@withContext
                                    val existing = db.conversations().get(convId)
                                    if (existing == null) {
                                        db.conversations().upsert(
                                            com.squelch.app.data.local.entity.ConversationEntity(
                                                id = convId,
                                                name = convName,
                                                lastMessageTimestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            }
                            navController.navigate(Screen.Conversation.createRoute(convId, convName, false)) {
                                popUpTo(Screen.NewChat.route) { inclusive = true }
                            }
                        },
                        onNewGroup = {
                            navController.navigate(Screen.NewGroup.route)
                        }
                    )
                }

                composable(Screen.NewGroup.route) {
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    CreateGroupScreen(
                        contacts = contacts,
                        onBack = { navController.popBackStack() },
                        onGroupCreated = { name, memberPubKeys ->
                            chatViewModel.createGroup(name, memberPubKeys)
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.Strangers.route) {
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    val strangers by chatViewModel.strangers.collectAsState()
                    StrangerMessagesScreen(
                        strangers = strangers,
                        onBack = { navController.popBackStack() },
                        onOpenChat = { senderEdPubHex, senderName ->
                            navController.navigate(Screen.Conversation.createRoute(senderEdPubHex, senderName, false))
                        },
                        onAddContact = { edPub, name ->
                            navController.navigate(Screen.UserSearch.route)
                        },
                        onBlock = { edPub ->
                            chatViewModel.blockSender(edPub)
                        }
                    )
                }

                composable(Screen.BlockedUsers.route) {
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    BlockedUsersScreen(
                        viewModel = chatViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.Conversation.route,
                    arguments = listOf(
                        navArgument("conversationId") { type = NavType.StringType },
                        navArgument("conversationName") { type = NavType.StringType },
                        navArgument("isGroup") { type = NavType.BoolType; defaultValue = false }
                    )
                ) { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                    val conversationName = java.net.URLDecoder.decode(
                        backStackEntry.arguments?.getString("conversationName") ?: "",
                        "UTF-8"
                    )
                    val isGroup = backStackEntry.arguments?.getBoolean("isGroup") ?: false
                    val signed = authRepository.signedIn()
                    val selfPubkey = signed?.let {
                        Identity.fromGoogleUid(it.googleUid).edPub.toHex()
                    } ?: ""

                    val recipientUid by produceState("", conversationId) {
                        val db = vaultRepository.db ?: return@produceState
                        try {
                            val contact = withContext(Dispatchers.IO) { db.contacts().get(conversationId) }
                            val uid = contact?.firebaseUid ?: ""
                            if (uid.isNotEmpty()) {
                                value = uid
                            } else {
                                val doc = withContext(Dispatchers.IO) {
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .whereEqualTo("edPub", conversationId)
                                        .limit(1)
                                        .get()
                                        .await()
                                }
                                if (!doc.isEmpty) {
                                    val firebaseUid = doc.documents[0].id
                                    val email = doc.documents[0].getString("email") ?: ""
                                    if (contact != null && email.isNotEmpty()) {
                                        withContext(Dispatchers.IO) {
                                            db.contacts().upsert(contact.copy(firebaseUid = firebaseUid, email = email))
                                        }
                                    }
                                    value = firebaseUid
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    val recipientEmail by produceState("", conversationId) {
                        val db = vaultRepository.db ?: return@produceState
                        try {
                            val contact = withContext(Dispatchers.IO) { db.contacts().get(conversationId) }
                            val email = contact?.email ?: ""
                            if (email.isNotEmpty()) {
                                value = email
                            } else {
                                val doc = withContext(Dispatchers.IO) {
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .whereEqualTo("edPub", conversationId)
                                        .limit(1)
                                        .get()
                                        .await()
                                }
                                if (!doc.isEmpty) {
                                    val fetchedEmail = doc.documents[0].getString("email") ?: ""
                                    if (contact != null && fetchedEmail.isNotEmpty()) {
                                        withContext(Dispatchers.IO) {
                                            db.contacts().upsert(contact.copy(email = fetchedEmail))
                                        }
                                    }
                                    value = fetchedEmail
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    ConversationScreen(
                        conversationId = conversationId,
                        conversationName = conversationName,
                        selfPubkey = selfPubkey,
                        recipientUid = recipientUid,
                        recipientEmail = recipientEmail,
                        isGroup = isGroup,
                        onBack = { navController.popBackStack() },
                        onGroupInfoClick = {
                            navController.navigate(Screen.GroupInfo.createRoute(conversationId, conversationName))
                        }
                    )
                }

                composable(
                    route = Screen.GroupInfo.route,
                    arguments = listOf(
                        navArgument("groupId") { type = NavType.StringType },
                        navArgument("groupName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                    val groupName = java.net.URLDecoder.decode(
                        backStackEntry.arguments?.getString("groupName") ?: "",
                        "UTF-8"
                    )
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    GroupInfoScreen(
                        groupId = groupId,
                        groupName = groupName,
                        onBack = { navController.popBackStack() },
                        viewModel = chatViewModel
                    )
                }

                composable(Screen.Contacts.route) {
                    val contactsChatViewModel: ChatViewModel = hiltViewModel()
                    ContactsScreen(
                        contacts = contacts,
                        onNavigateToAddContact = { navController.navigate(Screen.AddContact.route) },
                        onNavigateToMyQr = { navController.navigate(Screen.MyQr.route) },
                        onNavigateToUserSearch = { navController.navigate(Screen.UserSearch.route) },
                        onContactClick = { contact ->
                            val convId = contact.pubkey
                            val convName = contact.callsign.ifEmpty { contact.displayName }
                            MainScope().launch {
                                withContext(Dispatchers.IO) {
                                    val db = vaultRepository.db ?: return@withContext
                                    val existing = db.conversations().get(convId)
                                    if (existing == null) {
                                        db.conversations().upsert(
                                            com.squelch.app.data.local.entity.ConversationEntity(
                                                id = convId,
                                                name = convName,
                                                lastMessageTimestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            }
                            navController.navigate(Screen.Conversation.createRoute(convId, convName, false))
                        },
                        onDeleteContact = { pubkey ->
                            contactsChatViewModel.deleteContact(pubkey)
                        }
                    )
                }

                composable(Screen.AddContact.route) {
                    AddContactScreen(
                        onBack = { navController.popBackStack() },
                        onQrScanned = { contact ->
                            MainScope().launch {
                                withContext(Dispatchers.IO) {
                                    val db = vaultRepository.db
                                    val existing = db?.contacts()?.get(contact.edPub)
                                    db?.contacts()?.upsert(
                                        ContactEntity(
                                            pubkey = contact.edPub,
                                            firebaseUid = existing?.firebaseUid ?: "",
                                            xPub = contact.xPub,
                                            callsign = contact.callsign,
                                            displayName = contact.displayName,
                                            email = existing?.email ?: "",
                                            lastSeen = System.currentTimeMillis()
                                        )
                                    )
                                    Log.d("AppEntry", "QR contact upserted: ${contact.displayName}")
                                }
                                try {
                                    vaultRepository.pushContactsToCloud()
                                    Log.d("AppEntry", "Contacts pushed to cloud after QR scan")
                                } catch (e: Exception) {
                                    Log.e("AppEntry", "Push contacts failed: ${e.message}", e)
                                }
                            }
                            navController.popBackStack()
                        },
                        onFindByEmail = {
                            navController.navigate(Screen.UserSearch.route)
                        }
                    )
                }

                composable(Screen.UserSearch.route) {
                    UserSearchScreen(
                        onBack = { navController.popBackStack() },
                        onAddContact = { result ->
                            MainScope().launch {
                                withContext(Dispatchers.IO) {
                                    vaultRepository.db?.contacts()?.upsert(
                                        ContactEntity(
                                            pubkey = result.edPub,
                                            firebaseUid = result.googleUid,
                                            xPub = result.xPub,
                                            callsign = result.displayName,
                                            displayName = result.displayName,
                                            email = result.email,
                                            lastSeen = System.currentTimeMillis()
                                        )
                                    )
                                    Log.d("AppEntry", "Email contact upserted: ${result.displayName}")
                                }
                                try {
                                    vaultRepository.pushContactsToCloud()
                                    Log.d("AppEntry", "Contacts pushed to cloud after email search")
                                } catch (e: Exception) {
                                    Log.e("AppEntry", "Push contacts failed: ${e.message}", e)
                                }
                            }
                        }
                    )
                }

                composable(Screen.MyQr.route) {
                    val signed = authRepository.signedIn()
                    val selfContact = remember(signed) {
                        val uid = VaultSession.googleUidOrNull() ?: ""
                        val identity = if (uid.isNotEmpty()) Identity.fromGoogleUid(uid) else null
                        QrContact(
                            edPub = identity?.edPub?.toHex() ?: uid,
                            xPub = identity?.xPub?.toHex() ?: "",
                            callsign = signed?.displayName?.take(12) ?: "Unknown",
                            displayName = signed?.displayName ?: signed?.email ?: ""
                        )
                    }
                    MyQrScreen(
                        selfContact = selfContact,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Radar.route) {
                    val radarViewModel: RadarViewModel = hiltViewModel()
                    RadarScreen(
                        viewModel = radarViewModel,
                        onUserTap = { edPub, displayName ->
                            MainScope().launch {
                                withContext(Dispatchers.IO) {
                                    val db = vaultRepository.db ?: return@withContext
                                    val existing = db.conversations().get(edPub)
                                    if (existing == null) {
                                        db.conversations().upsert(
                                            com.squelch.app.data.local.entity.ConversationEntity(
                                                id = edPub,
                                                name = displayName,
                                                lastMessageTimestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            }
                            navController.navigate(Screen.Conversation.createRoute(edPub, displayName, false))
                        },
                        onPeerTap = { peerId, peerName ->
                            MainScope().launch {
                                withContext(Dispatchers.IO) {
                                    val db = vaultRepository.db ?: return@withContext
                                    val existing = db.conversations().get(peerId)
                                    if (existing == null) {
                                        db.conversations().upsert(
                                            com.squelch.app.data.local.entity.ConversationEntity(
                                                id = peerId,
                                                name = peerName,
                                                lastMessageTimestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            }
                            navController.navigate(Screen.Conversation.createRoute(peerId, peerName, false))
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    val signed = authRepository.signedIn()
                    SettingsScreen(
                        lockEnabled = vaultRepository.isLockEnabled,
                        isBackingUp = isBackingUp,
                        displayName = signed?.displayName ?: "",
                        email = signed?.email ?: "",
                        onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                        onNavigateToBlockedUsers = { navController.navigate(Screen.BlockedUsers.route) },
                        onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                        onSignOut = {
                            vaultRepository.signOut()
                            authRepository.signOut()
                            navController.navigate(Screen.SignIn.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onLock = {
                            vaultRepository.lock()
                            navController.navigate(Screen.Unlock.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onEnableBiometric = {
                            vaultRepository.enableBiometricLock(activity)
                        },
                        onDisableBiometric = {
                            vaultRepository.disableBiometricLock(activity)
                        },
                        onBackupNow = {
                            isBackingUp = true
                            MainScope().launch {
                                val hasAccess = driveBackupManager.ensureDriveAccess()
                                if (hasAccess) {
                                    performBackup(driveBackupManager, firestoreVaultManager, authRepository)
                                } else {
                                    withContext(Dispatchers.Main) {
                                        driveSignInLauncher.launch(driveBackupManager.getDriveSignInIntent())
                                    }
                                }
                                isBackingUp = false
                            }
                        },
                        onRestore = {
                            navController.navigate(Screen.Restore.route)
                        }
                    )
                }

                composable(Screen.Profile.route) {
                    val signed = authRepository.signedIn()
                    ProfileScreen(
                        displayName = signed?.displayName ?: "",
                        email = signed?.email ?: "",
                        googleUid = signed?.googleUid ?: "",
                        onBack = { navController.popBackStack() },
                        onDeleteAccount = {
                            vaultRepository.signOut()
                            authRepository.deleteAccount()
                            navController.navigate(Screen.SignIn.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}

private suspend fun performBackup(
    driveBackupManager: DriveBackupManager,
    firestoreVaultManager: FirestoreVaultManager,
    authRepository: AuthRepository
) {
    try {
        val signed = authRepository.signedIn() ?: return
        val vaultBlob = firestoreVaultManager.downloadVault(signed.googleUid) ?: return
        driveBackupManager.backupVault(vaultBlob)
    } catch (e: Exception) {
        android.util.Log.e("AppEntry", "Backup vault failed: ${e.message}", e)
    }
}

private fun writeUserProfile(authRepository: AuthRepository) {
    val signed = authRepository.signedIn() ?: return
    val uid = signed.googleUid
    val db = FirebaseFirestore.getInstance()
    val identity = Identity.fromGoogleUid(uid)
    val profile = mapOf(
        "email" to signed.email,
        "displayName" to signed.displayName,
        "edPub" to identity.edPub.toHex(),
        "xPub" to identity.xPub.toHex()
    )
    db.collection("users").document(uid).set(profile, com.google.firebase.firestore.SetOptions.merge())
}
