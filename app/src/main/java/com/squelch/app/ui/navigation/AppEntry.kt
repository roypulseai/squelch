package com.squelch.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.squelch.app.auth.AuthRepository
import com.squelch.app.auth.AuthState
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.data.repository.VaultRepository.VaultState
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.VaultSession
import com.squelch.app.qr.QrCodec
import com.squelch.app.qr.QrContact
import com.squelch.app.util.toHex
import com.squelch.app.ui.screens.chats.ChatsScreen
import com.squelch.app.ui.screens.contacts.AddContactScreen
import com.squelch.app.ui.screens.contacts.ContactsScreen
import com.squelch.app.ui.screens.contacts.MyQrScreen
import com.squelch.app.ui.screens.mesh.RadarScreen
import com.squelch.app.ui.screens.onboarding.MnemonicBackupScreen
import com.squelch.app.ui.screens.onboarding.PinEntryScreen
import com.squelch.app.ui.screens.onboarding.SignInScreen
import com.squelch.app.ui.screens.settings.SettingsScreen
import com.squelch.app.ui.screens.splash.SplashScreen

@Composable
fun AppEntry(
    authRepository: AuthRepository,
    vaultRepository: VaultRepository
) {
    val navController = rememberNavController()
    val authState by authRepository.state.collectAsState()
    val vaultState by vaultRepository.state.collectAsState()

    val startDestination = when {
        authState !is AuthState.SignedIn -> Screen.SignIn.route
        vaultState is VaultState.Unlocked -> Screen.Chats.route
        vaultState is VaultState.MnemonicPending -> Screen.PinSetup.route
        vaultState is VaultState.Provisioning -> Screen.PinSetup.route
        vaultState is VaultState.Locked -> Screen.PinUnlock.route
        vaultState is VaultState.MnemonicBackup -> Screen.MnemonicBackup.createRoute("pending")
        vaultState is VaultState.Error -> Screen.PinUnlock.route
        else -> Screen.Splash.route
    }

    LaunchedEffect(authState, vaultState) {
        when {
            authState is AuthState.SignedIn && vaultState is VaultState.Idle -> {
                vaultRepository.initDrive()
                vaultRepository.checkVaultState()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onTimeout = {
                        val dest = when {
                            authState !is AuthState.SignedIn -> Screen.SignIn.route
                            vaultState is VaultState.Unlocked -> Screen.Chats.route
                            else -> Screen.PinUnlock.route
                        }
                        navController.navigate(dest) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.SignIn.route) {
                SignInScreen(
                    authRepository = authRepository,
                    onSignedIn = {
                        navController.navigate(Screen.Splash.route) {
                            popUpTo(Screen.SignIn.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.PinSetup.route) {
                PinEntryScreen(
                    title = "Create PIN",
                    subtitle = "Set a PIN to protect your vault (4-8 digits)",
                    confirmMode = true,
                    loading = vaultState is VaultState.Encrypting || vaultState is VaultState.MnemonicPending,
                    errorMessage = (vaultState as? VaultState.Error)?.message,
                    onPinEntered = { pin ->
                        vaultRepository.generateMnemonic()
                        navController.navigate(Screen.MnemonicBackup.createRoute(pin)) {
                            popUpTo(Screen.PinSetup.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.PinUnlock.route) {
                PinEntryScreen(
                    title = "Unlock Vault",
                    subtitle = "Enter your PIN to unlock",
                    loading = vaultState is VaultState.Decrypting,
                    errorMessage = (vaultState as? VaultState.Error)?.message,
                    onPinEntered = { pin ->
                        vaultRepository.unlockWithPin(pin)
                    },
                    onMnemonicClicked = null
                )
            }

            composable(
                route = Screen.MnemonicBackup.route,
                arguments = listOf(navArgument("pin") { type = NavType.StringType })
            ) { backStackEntry ->
                val pin = backStackEntry.arguments?.getString("pin") ?: ""
                val vs by vaultRepository.state.collectAsState()

                when (val state = vs) {
                    is VaultState.MnemonicBackup -> {
                        MnemonicBackupScreen(
                            mnemonic = state.mnemonic,
                            onConfirmed = {
                                vaultRepository.provisionVault(pin = pin, mnemonic = state.mnemonic)
                                navController.navigate(Screen.Chats.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                    is VaultState.Encrypting -> {
                        PinEntryScreen(
                            title = "Encrypting vault...",
                            subtitle = "Uploading encrypted vault to Google Drive",
                            loading = true,
                            onPinEntered = { }
                        )
                    }
                    is VaultState.Unlocked -> {
                        LaunchedEffect(Unit) {
                            navController.navigate(Screen.Chats.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                    else -> {
                        MnemonicBackupScreen(
                            mnemonic = "generating...",
                            onConfirmed = { }
                        )
                    }
                }
            }

            composable(Screen.Chats.route) {
                ChatsScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
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
                    val mn = VaultSession.mnemonicOrNull()
                    if (mn != null) {
                        val identity = Identity.fromMnemonic(mn)
                        QrContact(
                            edPub = identity.edPub.toHex(),
                            xPub = identity.xPub.toHex(),
                            callsign = signed?.displayName?.take(12) ?: "Unknown",
                            displayName = signed?.displayName ?: signed?.email ?: ""
                        )
                    } else {
                        QrContact(
                            edPub = "",
                            xPub = "",
                            callsign = "Unknown",
                            displayName = signed?.email ?: ""
                        )
                    }
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
                    onSignOut = {
                        vaultRepository.signOut()
                        authRepository.signOut()
                        navController.navigate(Screen.SignIn.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onLock = {
                        vaultRepository.lock()
                        navController.navigate(Screen.PinUnlock.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
