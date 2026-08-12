package com.squelch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.squelch.app.auth.AuthState
import com.squelch.app.ui.OnboardingViewModel

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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(60.dp))

        Text(
            text = "SQUELCH",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "p2p chat, serverless, cross-mobile",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(28.dp))

        StatusBlock(
            title = "ACCOUNT",
            lines = buildList {
                when (val s = state) {
                    AuthState.Idle -> add("not signed in")
                    AuthState.SigningIn -> add("(waiting for Google…)")
                    is AuthState.SignedIn -> {
                        add("email  : ${s.email}")
                        add("uid    : ${s.googleUid.take(16)}…")
                        add("displayName: ${s.displayName ?: "(unset)"}")
                    }
                    is AuthState.Error -> add("ERROR: ${s.message}")
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        StatusBlock(
            title = "DRIVE FOLDER",
            lines = buildList {
                when (val d = drive) {
                    OnboardingViewModel.DriveStatus.Idle -> add("not yet checked")
                    OnboardingViewModel.DriveStatus.Loading -> add("(checking…)")
                    is OnboardingViewModel.DriveStatus.Ok -> {
                        add("name : ${d.folderName}")
                        add("id   : ${d.folderId}")
                    }
                    is OnboardingViewModel.DriveStatus.Error -> add("ERROR: ${d.message}")
                }
            }
        )

        Spacer(Modifier.height(28.dp))

        ActionButton(
            label = when (state) {
                AuthState.Idle -> "[ SIGN IN WITH GOOGLE ]"
                is AuthState.SignedIn -> "[ PING DRIVE ]"
                else -> "[ WORKING… ]"
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
            ActionButton(label = "[ SIGN OUT ]", enabled = true) { vm.signOut() }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "v0.2.0  ·  M2 scaffold",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun StatusBlock(title: String, lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        lines.forEach { line ->
            Text(
                text = line,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface
    val fg = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .let { if (enabled) it.clickable { onClick() } else it }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace
        )
    }
}
