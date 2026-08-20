package com.squelch.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.squelch.app.auth.AuthRepository
import com.squelch.app.auth.AuthState
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.data.repository.VaultRepository.VaultState
import com.squelch.app.crypto.VaultSession
import com.squelch.app.qr.QrContact
import com.squelch.app.util.toHex
import com.squelch.app.ui.screens.chats.ChatsScreen
import com.squelch.app.ui.screens.chats.ConversationScreen
import com.squelch.app.ui.screens.chats.NewChatScreen
import com.squelch.app.ui.screens.contacts.AddContactScreen
import com.squelch.app.ui.screens.contacts.ContactsScreen
import com.squelch.app.ui.screens.contacts.MyQrScreen
import com.squelch.app.ui.screens.mesh.RadarScreen
import com.squelch.app.ui.screens.onboarding.SignInScreen
import com.squelch.app.ui.screens.onboarding.BiometricGateScreen
import com.squelch.app.ui.screens.settings.SettingsScreen

@Composable
fun AppEntry(
    authRepository: AuthRepository,
    vaultRepository: VaultRepository
) {
    val navController = rememberNavController()
    val authState by authRepository.state.collectAsState()
    val vaultState by vaultRepository.state.collectAsState()
    val activity = LocalContext.current as FragmentActivity

    val isSignedIn = authState is AuthState.SignedIn

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

    LaunchedEffect(vaultState) {
        when (vaultState) {
            is VaultState.Unlocked -> {
                val current = navController.currentDestination?.route
                if (current == Screen.SignIn.route || current == Screen.Unlock.route) {
                    navController.navigate(Screen.Chats.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
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

            composable(Screen.Chats.route) {
                ChatsScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
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
                    onNavigateToMyQr = { navController.navigate(Screen.MyQr.route) }
                )
            }

            composable(Screen.AddContact.route) {
                AddContactScreen(
                    onBack = { navController.popBackStack() },
                    onQrScanned = { contact ->
                        kotlinx.coroutines.MainScope().launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                vaultRepository.db?.contacts()?.upsert(
                                    com.squelch.app.data.local.entity.ContactEntity(
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

            composable(Screen.MyQr.route) {
                val signed = authRepository.signedIn()
                val selfContact = remember(signed) {
                    val uid = VaultSession.googleUidOrNull() ?: ""
                    val identity = if (uid.isNotEmpty()) com.squelch.app.crypto.Identity.fromGoogleUid(uid) else null
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
                    }
                )
            }
        }
    }
}
