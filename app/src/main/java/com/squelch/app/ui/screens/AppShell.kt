package com.squelch.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.R
import com.squelch.app.crypto.VaultSession
import com.squelch.app.ui.OnboardingViewModel
import com.squelch.app.ui.components.PrimaryButton
import com.squelch.app.ui.components.monoStyle

/** Post-unlock surface (M7). Lists known peers + last messages,
 *  lets the user select one, type a message, and sends through
 *  MeshEngine.sendChat. The active peer is held in local UI state. */
@Composable
fun AppShell(vm: OnboardingViewModel) {
    val meshStatus by vm.meshStatus.collectAsState()
    val peers by vm.meshPeers.collectAsState()
    val messages by vm.meshMessages.collectAsState()
    val relay by vm.relayStatus.collectAsState()

    var selectedPeer by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }

    val fp = VaultSession.kDbOrEmpty().take(4).joinToString("") { "%02x".format(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "SQUELCH",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        letterSpacing = 4.sp
                    )
                )
                Text(
                    "unlocked  -  ${fp}...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = monoStyle(10)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "ONLINE RELAY",
            color = MaterialTheme.colorScheme.secondary,
            style = monoStyle(10).copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "url   : ${relay.url}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(10)
        )
        Text(
            text = "state : " + when {
                relay.connected -> "CONNECTED"
                relay.connecting -> "connecting…"
                relay.error != null -> "error: ${relay.error}"
                else -> "off"
            },
            color = when {
                relay.connected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = monoStyle(10)
        )
        Text(
            "PEERS (${meshStatus.linkedPeers})",
            color = MaterialTheme.colorScheme.secondary,
            style = monoStyle(10).copy(fontWeight = FontWeight.Bold)
        )

        if (peers.isEmpty()) {
            Text(
                "> no peers yet\n> tap START MESH then bring two devices close.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = monoStyle(11)
            )
        } else {
            peers.values.take(8).forEach { peer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (selectedPeer == peer.endpointId) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.background
                        )
                        .clickable { selectedPeer = peer.endpointId }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedPeer == peer.endpointId) "*" else " ",
                        color = MaterialTheme.colorScheme.primary,
                        style = monoStyle(13)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = peer.displayName.take(20),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = monoStyle(12)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = peer.endpointId.take(6),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = monoStyle(10)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "MESSAGES (last ${messages.size})",
            color = MaterialTheme.colorScheme.secondary,
            style = monoStyle(10).copy(fontWeight = FontWeight.Bold)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            if (messages.isEmpty()) {
                Text(
                    text = "(no messages yet)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = monoStyle(11)
                )
            } else {
                Column {
                    messages.takeLast(8).forEach { m ->
                        Text(
                            "${m.fromPub.take(6)}: ${m.inner.text}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = monoStyle(11)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (text.isEmpty()) "> msg..." else text,
                    color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    style = monoStyle(13)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "   TYPE MSG   ",
                modifier = Modifier
                    .clickable { text = text + " " }
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = monoStyle(11)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "   SEND   ",
                modifier = Modifier
                    .clickable {
                        val peerId = selectedPeer
                        if (text.isNotBlank() && peerId != null) {
                            vm.sendToPeerEndpoint(peerId, text.trim())
                            text = ""
                        }
                    }
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "   X   ",
                modifier = Modifier.clickable { text = "" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = monoStyle(11)
            )
        }

        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "   TOGGLE MESH   ",
                modifier = Modifier
                    .clickable { vm.toggleMesh() }
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "   LOCK   ",
                modifier = Modifier
                    .clickable { vm.lock() }
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "v0.7.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = monoStyle(9)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
