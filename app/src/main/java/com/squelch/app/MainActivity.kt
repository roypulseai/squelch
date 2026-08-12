package com.squelch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import com.squelch.app.auth.AuthState
import com.squelch.app.ui.OnboardingViewModel
import com.squelch.app.ui.screens.SignedInStub
import com.squelch.app.ui.screens.SignInScreen

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
    val auth by vm.auth.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
            task.addOnCompleteListener { completed ->
                vm.onSignInResult(completed)
            }
        } catch (e: Exception) {
            vm.onSignInResult(
                com.google.android.gms.tasks.Tasks.forException(e)
            )
        }
    }

    var pingedThisSession by remember { mutableStateOf(false) }

    when (val s = auth) {
        is AuthState.SignedIn -> {
            LaunchedEffect(s.email) {
                if (!pingedThisSession) {
                    pingedThisSession = true
                    vm.pingDrive()
                }
            }
            SignedInStub(
                email = s.email,
                uid = s.googleUid,
                onSignOut = {
                    pingedThisSession = false
                    vm.signOut()
                }
            )
        }
        else -> {
            SignInScreen(
                vm = vm,
                onSignedIn = { /* implicit via collected state */ },
                onRequestSignIn = {
                    launcher.launch(vm.signInIntent())
                }
            )
        }
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
