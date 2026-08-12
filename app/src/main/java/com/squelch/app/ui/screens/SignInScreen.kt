package com.squelch.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.R
import com.squelch.app.auth.AuthState
import com.squelch.app.ui.OnboardingViewModel
import com.squelch.app.ui.components.PrimaryButton
import com.squelch.app.ui.components.StatusCard
import com.squelch.app.ui.components.GhostButton
import com.squelch.app.ui.components.monoStyle

@Composable
fun SignInScreen(
    vm: OnboardingViewModel,
    onSignedIn: () -> Unit = {},
    onRequestSignIn: () -> Unit = {}
) {
    val state by vm.auth.collectAsState()
    val drive by vm.driveStatus.collectAsState()

    LaunchedEffect(state) {
        if (state is AuthState.SignedIn) onSignedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "SQUELCH",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 4.sp
            )
        )

        Text(
            text = "p2p  ·  serverless  ·  cross-mobile",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11).copy(letterSpacing = 1.sp)
        )

        Spacer(Modifier.height(28.dp))

        StatusCard(title = "ACCOUNT") {
            Text(
                text = statusAccount(state),
                color = MaterialTheme.colorScheme.onSurface,
                style = monoStyle(13)
            )
        }

        Spacer(Modifier.height(8.dp))

        StatusCard(title = "DRIVE FOLDER") {
            Text(
                text = statusDrive(drive),
                color = MaterialTheme.colorScheme.onSurface,
                style = monoStyle(13)
            )
        }

        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            label = when (state) {
                AuthState.Idle -> "   SIGN IN WITH GOOGLE   "
                is AuthState.SignedIn -> "   PING DRIVE   "
                else -> "   WORKING…   "
            },
            enabled = state !is AuthState.SigningIn
        ) {
            when (state) {
                AuthState.Idle -> onRequestSignIn()
                is AuthState.SignedIn -> vm.pingDrive()
                else -> Unit
            }
        }

        if (state is AuthState.SignedIn) {
            Spacer(Modifier.height(8.dp))
            GhostButton(label = "   SIGN OUT   ") {
                vm.signOut()
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "your data lives only in your Google Drive.\nno servers touch your messages.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11)
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "v0.3.0  ·  M2 scaffold",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11)
        )
        Spacer(Modifier.height(20.dp))
    }
}

private fun statusAccount(state: AuthState): String = when (state) {
    AuthState.Idle -> "not signed in"
    AuthState.SigningIn -> "(waiting for Google…)"
    is AuthState.SignedIn ->
        "email : ${state.email}\n" +
        "uid   : ${state.googleUid.take(20)}…\n" +
        "name  : ${state.displayName ?: "(unset)"}"
    is AuthState.Error -> "ERROR: ${state.message}"
}

private fun statusDrive(drive: OnboardingViewModel.DriveStatus): String = when (drive) {
    OnboardingViewModel.DriveStatus.Idle -> "not yet checked"
    OnboardingViewModel.DriveStatus.Loading -> "(checking /squelch/…)"
    is OnboardingViewModel.DriveStatus.Ok ->
        "name : ${drive.folderName}\n" +
        "id   : ${drive.folderId}"
    is OnboardingViewModel.DriveStatus.Error -> "ERROR: ${drive.message}"
}
