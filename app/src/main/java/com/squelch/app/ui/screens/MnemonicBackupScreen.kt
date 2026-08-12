package com.squelch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.ui.components.PrimaryButton
import com.squelch.app.ui.components.StatusCard
import com.squelch.app.ui.components.monoStyle

/**
 * Display a freshly generated 24-word BIP-39 mnemonic in a 4-column grid.
 * Two acknowledgment checkboxes must both flip before the I HAVE WRITTEN
 * DOWN button activates.
 */
@Composable
fun MnemonicBackupScreen(
    mnemonic: String,
    pinLength: Int,
    onProvision: (pin: String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var ackOwn by remember { mutableStateOf(false) }
    var ackTrust by remember { mutableStateOf(false) }
    val words = remember(mnemonic) { mnemonic.split(" ") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            "BACK UP YOUR RECOVERY PHRASE",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        )
        Text(
            "This 24-word phrase is the ONLY way to recover your identity if you lose your phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11)
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                items(words.withIndex().toList()) { (idx, word) ->
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = monoStyle(9)
                        )
                        Text(
                            text = word,
                            color = MaterialTheme.colorScheme.primary,
                            style = monoStyle(13).copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        StatusCard(title = "CHOOSE A 6-DIGIT PIN") {
            Text(
                text = if (pin.length == pinLength) "pin: $pin" else "pin: ${pin.ifEmpty { "______" }}",
                color = MaterialTheme.colorScheme.onSurface,
                style = monoStyle(13)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").forEach { d ->
                PinButton(d) { value ->
                    if (pin.length < pinLength) pin = pin + value
                }
            }
            PinButton("X") { _ -> pin = pin.dropLast(1) }
        }

        Spacer(Modifier.height(16.dp))

        AckRow(
            label = "I have written my 24-word phrase down on paper.",
            checked = ackOwn,
            onToggle = { ackOwn = !ackOwn }
        )
        Spacer(Modifier.height(6.dp))
        AckRow(
            label = "I understand Squelch cannot recover it for me.",
            checked = ackTrust,
            onToggle = { ackTrust = !ackTrust }
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            label = "   PROVISION VAULT   ",
            enabled = ackOwn && ackTrust && pin.length == pinLength
        ) { onProvision(pin) }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "   CANCEL   ",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11).copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clickable { onCancel() }
                .padding(8.dp)
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PinButton(label: String, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick(label) },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary, style = monoStyle(13))
    }
}

@Composable
private fun AckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("x", color = MaterialTheme.colorScheme.onPrimary, style = monoStyle(12))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = monoStyle(11))
    }
}
