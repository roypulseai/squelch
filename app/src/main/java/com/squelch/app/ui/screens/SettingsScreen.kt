package com.squelch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.crypto.VaultSession
import com.squelch.app.ui.OnboardingViewModel
import com.squelch.app.ui.components.PrimaryButton
import com.squelch.app.ui.components.StatusCard
import com.squelch.app.ui.components.monoStyle

/** Minimal settings surface: shows the relay URL + DB fingerprint, and
 *  exposes Lock / Sign Out actions. Future milestones wire these to
 *  the actual SettingsRepository (change-PIN, export vault, etc.). */
@Composable
fun SettingsScreen(
    vm: OnboardingViewModel,
    onBack: () -> Unit
) {
    val relay by vm.relayStatus.collectAsState()
    val mesh by vm.meshStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "SETTINGS",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 3.sp
            )
        )
        Spacer(Modifier.height(20.dp))

        StatusCard(title = "ONLINE RELAY") {
            Text("url    : ${relay.url}", color = MaterialTheme.colorScheme.onSurface, style = monoStyle(12))
            Text("state  : " + when {
                relay.connected -> "CONNECTED"
                relay.connecting -> "connecting..."
                relay.error != null -> "error: ${relay.error}"
                else -> "off"
            }, color = if (relay.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = monoStyle(12))
            Text(
                "change relay URL: planned for v0.10.1 (SettingsRepository)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = monoStyle(10)
            )
        }

        Spacer(Modifier.height(12.dp))

        StatusCard(title = "VAULT") {
            val fp = VaultSession.kDbOrEmpty().take(4).joinToString("") { "%02x".format(it) }
            Text("db fp  : $fp...", color = MaterialTheme.colorScheme.onSurface, style = monoStyle(12))
            Text("change PIN: planned for v0.10.1", color = MaterialTheme.colorScheme.onSurfaceVariant, style = monoStyle(10))
            Text("export identity: planned for v0.10.1", color = MaterialTheme.colorScheme.onSurfaceVariant, style = monoStyle(10))
        }

        Spacer(Modifier.height(12.dp))

        StatusCard(title = "MESH") {
            Text("engine : ${if (mesh.running) "RUNNING" else "stopped"}", color = if (mesh.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = monoStyle(12))
            Text("relay  : ${if (relay.connected) "CONNECTED" else "off"}", color = if (relay.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = monoStyle(12))
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(label = "   LOCK NOW   ", enabled = true, onClick = { vm.lock() })
        Spacer(Modifier.height(8.dp))
        PrimaryButton(label = "   SIGN OUT   ", enabled = true, onClick = { vm.signOut() })
        Spacer(Modifier.height(8.dp))
        Text(
            "   BACK   ",
            color = MaterialTheme.colorScheme.onSurface,
            style = monoStyle(12).copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onBack() }
                .padding(vertical = 10.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text("v0.10.0  -  M10", color = MaterialTheme.colorScheme.onSurfaceVariant, style = monoStyle(10))
        Spacer(Modifier.height(20.dp))
    }
}