package com.squelch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.auth.AuthState
import com.squelch.app.ui.OnboardingViewModel
import com.squelch.app.ui.components.monoStyle
import com.squelch.app.ui.screens.AppShell
import com.squelch.app.ui.screens.MnemonicBackupScreen
import com.squelch.app.ui.screens.PinEntryScreen
import com.squelch.app.ui.screens.SignedInStub
import com.squelch.app.ui.screens.SignInScreen
import com.squelch.app.ui.screens.SplashScreen

class MainActivity : ComponentActivity() {

    private val vm: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SquelchTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: OnboardingViewModel) {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onTimeout = { showSplash = false })
        return
    }

    val auth by vm.auth.collectAsState()
    val vault by vm.vaultFlow.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
            task.addOnCompleteListener { completed -> vm.onSignInResult(completed) }
        } catch (e: Exception) {
            vm.onSignInResult(com.google.android.gms.tasks.Tasks.forException(e))
        }
    }

    var pingedThisSession by remember { mutableStateOf(false) }
    var probedThisSignin by remember { mutableStateOf(false) }

    LaunchedEffect(auth) {
        val s = auth
        if (s is AuthState.SignedIn) {
            if (!pingedThisSession) {
                pingedThisSession = true
                vm.pingDrive()
            }
            if (!probedThisSignin && vault is OnboardingViewModel.VaultFlowState.Idle) {
                probedThisSignin = true
                vm.checkVaultState()
            }
        } else {
            pingedThisSession = false
            probedThisSignin = false
        }
    }

    when (val s = auth) {
        is AuthState.SignedIn -> AppShellRouter(vm, s)
        else -> SignInScreen(
            vm = vm,
            onRequestSignIn = { launcher.launch(vm.signInIntent()) }
        )
    }
}

@Composable
private fun AppShellRouter(vm: OnboardingViewModel, signed: AuthState.SignedIn) {
    val vaultState by vm.vaultFlow.collectAsState()
    val current = vaultState

    when {
        current == OnboardingViewModel.VaultFlowState.Idle ||
        current == OnboardingViewModel.VaultFlowState.Probing -> SignedInStub(
            vm = vm,
            email = signed.email,
            uid = signed.googleUid,
            onSignOut = { vm.signOut() }
        )

        current == OnboardingViewModel.VaultFlowState.Locked -> PinEntryScreen(
            pinLength = 6,
            onPinSubmit = { pin -> vm.unlockWithPin(signed.googleUid, pin) },
            onCancel = { vm.signOut() }
        )

        current == OnboardingViewModel.VaultFlowState.Provisioning -> LaunchedMnemonic(
            vm = vm,
            googleUid = signed.googleUid
        )

        current is OnboardingViewModel.VaultFlowState.MnemonicBackup ->
            MnemonicBackupScreen(
                mnemonic = current.mnemonic,
                pinLength = 6,
                onProvision = { pin -> vm.provisionVault(signed.googleUid, pin, current.mnemonic) },
                onCancel = { vm.signOut() }
            )

        current is OnboardingViewModel.VaultFlowState.Error -> SignedInStub(
            vm = vm,
            email = signed.email,
            uid = signed.googleUid,
            onSignOut = { vm.signOut() }
        )

        current == OnboardingViewModel.VaultFlowState.Encrypting ||
        current == OnboardingViewModel.VaultFlowState.Decrypting ||
        current == OnboardingViewModel.VaultFlowState.MnemonicPending ||
        current == OnboardingViewModel.VaultFlowState.Unlocked -> AppShell(vm = vm)
    }
}

@Composable
private fun LaunchedMnemonic(vm: OnboardingViewModel, googleUid: String) {
    LaunchedEffect(googleUid) { vm.generateMnemonicAndPreview() }
    EncryptingPane()
}

@Composable
private fun EncryptingPane() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(200.dp))
        monoStyle(36).toString()
        Text(
            text = "...",
            color = MaterialTheme.colorScheme.primary,
            style = monoStyle(36)
        )
    }
}

@Composable
private fun SquelchTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF00FF41),
        onPrimary = Color(0xFF000000),
        secondary = Color(0xFFFFB000),
        onSecondary = Color(0xFF000000),
        background = Color(0xFF0A0A12),
        onBackground = Color(0xFF00FF41),
        surface = Color(0xFF11111C),
        onSurface = Color(0xFF00FF41),
        surfaceVariant = Color(0xFF11111C),
        onSurfaceVariant = Color(0xFF0D5C1E),
        outline = Color(0xFF1F6B31)
    )
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal
    )
    val typography = MaterialTheme.typography.copy(
        displaySmall = MaterialTheme.typography.displaySmall.merge(monoStyle.copy(fontSize = 36.sp)),
        titleLarge = MaterialTheme.typography.titleLarge.merge(monoStyle.copy(fontSize = 22.sp)),
        titleMedium = MaterialTheme.typography.titleMedium.merge(monoStyle.copy(fontSize = 17.sp)),
        bodyLarge = MaterialTheme.typography.bodyLarge.merge(monoStyle.copy(fontSize = 15.sp)),
        bodyMedium = MaterialTheme.typography.bodyMedium.merge(monoStyle.copy(fontSize = 13.sp)),
        labelLarge = MaterialTheme.typography.labelLarge.merge(monoStyle.copy(fontSize = 14.sp)),
        labelMedium = MaterialTheme.typography.labelMedium.merge(monoStyle.copy(fontSize = 11.sp))
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
