package com.squelch.app.ui.navigation

import android.app.Activity
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
import com.squelch.app.auth.AuthRepository
import com.squelch.app.auth.AuthState
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.VaultSession
import com.squelch.app.data.local.entity.ContactEntity
import com.squelch.app.data.remote.DriveBackupManager
import com.squelch.app.data.remote.FirestoreVaultManager
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.data.repository.VaultRepository.VaultState
import com.squelch.app.qr.QrContact
import com.squelch.app.ui.screens.chats.ChatsScreen
import com.squelch.app.ui.screens.chats.ConversationScreen
import com.squelch.app.ui.screens.chats.NewChatScreen
import com.squelch.app.ui.screens.contacts.AddContactScreen
import com.squelch.app.ui.screens.contacts.ContactsScreen
import com.squelch.app.ui.screens.contacts.MyQrScreen
import com.squelch.app.ui.screens.contacts.UserSearchScreen
import com.squelch.app.ui.screens.mesh.RadarScreen
import com.squelch.app.ui.screens.onboarding.BiometricGateScreen
import com.squelch.app.ui.screens.onboarding.RestoreScreen
import com.squelch.app.ui.screens.onboarding.SignInScreen
import com.squelch.app.ui.screens.settings.SettingsScreen
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
    firestoreVaultManager: FirestoreVaultManager
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
        vaultState is VaultState.Unlocked -> Screen.Chats.route
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
                val current = navController.currentDestination?.route
                if (current == Screen.SignIn.route || current == Screen.Unlock.route) {
                    if (showRestore) {
                        navController.navigate(Screen.Restore.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Chats.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            else -> {}
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes

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
                            navController.navigate(Screen.Chats.route) {
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

                composable(Screen.Chats.route) {
                    ChatsScreen(
                        onNavigateToConversation = { id, name ->
                            navController.navigate(Screen.Conversation.createRoute(id, name))
                        },
                        onNavigateToNewChat = {
                            navController.navigate(Screen.NewChat.route)
                        }
                    )
                }

                composable(Screen.NewChat.route) {
                    NewChatScreen(
                        database = vaultRepository.db,
                        onBack = { navController.popBackStack() },
                        onContactSelected = { contact ->
                            val convId = contact.pubkey
                            val convName = contact.callsign.ifEmpty { contact.displayName }
                            navController.navigate(Screen.Conversation.createRoute(convId, convName)) {
                                popUpTo(Screen.NewChat.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = Screen.Conversation.route,
                    arguments = listOf(
                        navArgument("conversationId") { type = NavType.StringType },
                        navArgument("conversationName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                    val conversationName = backStackEntry.arguments?.getString("conversationName") ?: ""
                    val signed = authRepository.signedIn()
                    val selfPubkey = signed?.googleUid ?: ""

                    ConversationScreen(
                        conversationId = conversationId,
                        conversationName = conversationName,
                        selfPubkey = selfPubkey,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Contacts.route) {
                    ContactsScreen(
                        onNavigateToAddContact = { navController.navigate(Screen.AddContact.route) },
                        onNavigateToMyQr = { navController.navigate(Screen.MyQr.route) },
                        onNavigateToUserSearch = { navController.navigate(Screen.UserSearch.route) }
                    )
                }

                composable(Screen.AddContact.route) {
                    AddContactScreen(
                        onBack = { navController.popBackStack() },
                        onQrScanned = { contact ->
                            MainScope().launch {
                                withContext(Dispatchers.IO) {
                                    vaultRepository.db?.contacts()?.upsert(
                                        ContactEntity(
                                            pubkey = contact.edPub,
                                            xPub = contact.xPub,
                                            callsign = contact.callsign,
                                            displayName = contact.displayName,
                                            lastSeen = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                            navController.popBackStack()
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
                                            xPub = result.xPub,
                                            callsign = result.displayName,
                                            displayName = result.displayName,
                                            lastSeen = System.currentTimeMillis()
                                        )
                                    )
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
                    RadarScreen()
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        lockEnabled = vaultRepository.isLockEnabled,
                        isBackingUp = isBackingUp,
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
