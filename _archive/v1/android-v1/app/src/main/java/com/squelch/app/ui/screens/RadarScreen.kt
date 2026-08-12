package com.squelch.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squelch.app.db.ContactEntity
import com.squelch.app.mesh.MeshEngine
import com.squelch.app.ui.StatusBar
import java.util.concurrent.ThreadLocalRandom

/** Deterministic pseudo-position from a peer key so blips don't jump around. */
private fun blipPos(seed: String, index: Int, total: Int): Pair<Float, Float> {
    val h = seed.hashCode().let { if (it == Int.MIN_VALUE) 1 else kotlin.math.abs(it) }
    val rnd = ThreadLocalRandom.current()
    val angle = (h * 0.61803f + index) % 1.0f * (Math.PI.toFloat() * 2f)
    val radius = 0.25f + 0.65f * ((h ushr 4) % 100) / 100f
    val cx = 0.5f + radius * 0.45f * kotlin.math.cos(angle)
    val cy = 0.5f + radius * 0.45f * kotlin.math.sin(angle)
    return cx to cy
}

@Composable
fun RadarScreen(
    meshStatus: MeshEngine.MeshStatus,
    myCallsign: String,
    myFingerprint: String,
    contacts: List<ContactEntity>,
    linkedPeers: Set<String>
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatusBar("$myCallsign $myFingerprint  |  links:${meshStatus.links}  peers:${meshStatus.peers}  fwd:${meshStatus.packetsForwarded}")
        val stroke = MaterialTheme.colorScheme.outline
        Box(Modifier.weight(1f).fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                for (ring in 1..3) {
                    drawCircle(
                        color = stroke,
                        radius = size.minDimension * ring / 6f,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
                drawCircle(
                    color = stroke,
                    radius = 2.dp.toPx(),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                for (i in 0 until 8) {
                    val a = i * Math.PI.toFloat() / 4f
                    drawLine(
                        color = stroke,
                        start = center,
                        end = Offset(
                            center.x + kotlin.math.cos(a) * size.minDimension / 2f,
                            center.y + kotlin.math.sin(a) * size.minDimension / 2f
                        ),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            Text(
                text = myCallsign,
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        }
        contacts.take(14).forEachIndexed { index, contact ->
            val (fx, fy) = blipPos(contact.pubkey, index, contacts.size)
            Box(Modifier.padding(horizontal = 12.dp, vertical = 1.dp)) {
                Text(
                    text = blipLine(contact, fx, fy, linkedPeers.contains(contact.pubkey)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (linkedPeers.contains(contact.pubkey)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (contacts.isEmpty()) {
            Text(
                text = "> NO SIGNALS. MOVE CLOSER.\n> SCANNING BLE + NFC...",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun blipLine(c: ContactEntity, fx: Float, fy: Float, linked: Boolean): String {
    val dot = if (linked) "*" else "o"
    val call = c.callsign.ifBlank { c.pubkey.take(8) }
    return "  $dot  [${(fx * 100).toInt()},${(fy * 100).toInt()}]  $call"
}
