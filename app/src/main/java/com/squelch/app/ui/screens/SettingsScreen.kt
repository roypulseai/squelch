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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Settings screen. Now wires real Change-PIN + Export-Identity + a
 *  future 'contacts restore' entry (M-online). */
@Composable
fun SettingsScreen(
    vm: OnboardingViewModel,
    onBack: () -> Unit
) {
    val relay by vm.relayStatus.collectAsState()
    val mesh by vm.meshStatus.collectAsState()

    var route by remember { mutableStateOf(SettingsRoute.Main) }

    when (route) {
        SettingsRoute.Main -> SettingsMain(
            vm = vm,
            relay = relay,
            mesh = mesh,
            onBack = onBack,
            onChangePin = { route = SettingsRoute.ChangePin },
            onExportIdentity = { route = SettingsRoute.ExportIdentity }
        )
        SettingsRoute.ChangePin -> ChangePinScreen(
            vm = vm,
            pinLength = 6,
            onBack = { route = SettingsRoute.Main }
        )
        SettingsRoute.ExportIdentity -> ExportIdentityScreen(
            vm = vm,
            onBack = { route = SettingsRoute.Main }
        )
    }
}

private enum class SettingsRoute { Main, ChangePin, ExportIdentity }

@Composable
private fun SettingsMain(
    vm: OnboardingViewModel,
    relay: com.squelch.app.mesh.online.RelayTransport.RelayStatus,
    mesh: com.squelch.app.mesh.MeshEngine.MeshStatus,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onExportIdentity: () -> Unit
) {
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
        }

        Spacer(Modifier.height(12.dp))

        StatusCard(title = "VAULT") {
            val fp = VaultSession.kDbOrEmpty().take(4).joinToString("") { "%02x".format(it) }
            Text("db fp  : $fp...", color = MaterialTheme.colorScheme.onSurface, style = monoStyle(12))
        }

        Spacer(Modifier.height(12.dp))

        StatusCard(title = "ACTIONS") {
            ActionRow("CHANGE PIN", "rotate the master PIN + re-key vault") { onChangePin() }
            ActionRow("EXPORT IDENTITY", "base64 blob of the 32-byte seed") { onExportIdentity() }
            ActionRow("RESTORE CONTACTS", "pull contacts from another device's vault", null)
        }

        Spacer(Modifier.height(12.dp))

        StatusCard(title = "MESH") {
            Text("engine : ${if (mesh.running) "RUNNING" else "stopped"}",
                color = if (mesh.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = monoStyle(12))
            Text("relay  : ${if (relay.connected) "CONNECTED" else "off"}",
                color = if (relay.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = monoStyle(12))
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
        Text("v0.11.0  -  M-online+", color = MaterialTheme.colorScheme.onSurfaceVariant, style = monoStyle(10))
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.background)
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = (if (onClick != null) "   " else "   [stub] ") + title,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(12).copy(fontWeight = FontWeight.Bold)
        )
        if (subtitle != null) {
            Text(
                text = "         " + subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = monoStyle(10)
            )
        }
    }
}