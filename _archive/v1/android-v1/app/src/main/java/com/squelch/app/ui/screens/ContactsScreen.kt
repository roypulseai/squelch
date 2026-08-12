package com.squelch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squelch.app.db.ContactEntity
import com.squelch.app.mesh.MeshEngine
import com.squelch.app.mesh.TrustLevel
import com.squelch.app.transport.nfc.NfcTapManager
import com.squelch.app.ui.LocalActivity
import com.squelch.app.ui.StatusBar
import com.squelch.app.ui.hhmm

@Composable
fun ContactsScreen(
    contacts: List<ContactEntity>,
    meshStatus: MeshEngine.MeshStatus,
    linkedPeers: Set<String>,
    onIdentityRead: (ByteArray, ByteArray, ByteArray) -> Unit
) {
    val activity = LocalActivity.current
    var pairing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { NfcTapManager(it).disable() }
        }
    }

    LaunchedEffect(pairing, activity) {
        if (!pairing || activity == null) return@LaunchedEffect
        val manager = NfcTapManager(activity)
        manager.enable(object : NfcTapManager.Callback {
            override fun onIdentityRead(edPub: ByteArray, xPub: ByteArray, nonce: ByteArray) {
                pairing = false
                status = "> PAIRED ${Bytes6(edPub)}  V"
                onIdentityRead(edPub, xPub, nonce)
            }

            override fun onError(message: String) {
                pairing = false
                status = "> $message"
            }
        })
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatusBar("CONTACTS  |  ${contacts.size} known")

        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                text = if (pairing) "[TAP OTHER PHONE NOW...]" else "[TAP TO PAIR]",
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    .clickable { if (!pairing) pairing = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (status.isNotEmpty()) {
            Text(
                status,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "> NO CONTACTS YET.\n> MEET SOMEONE, OR TAP TO PAIR.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Column
        }

        LazyColumn {
            items(contacts) { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = TrustLevel.glyph(c.trustLevel),
                        color = when (c.trustLevel) {
                            TrustLevel.VERIFIED -> MaterialTheme.colorScheme.primary
                            TrustLevel.RELAYED -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.callsign.ifBlank { c.pubkey.take(8) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "seen ${hhmm(c.lastSeen)}  ${if (linkedPeers.contains(c.pubkey)) "LINKED" else "offline"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun Bytes6(b: ByteArray): String =
    b.take(3).joinToString("") { "%02x".format(it) }
