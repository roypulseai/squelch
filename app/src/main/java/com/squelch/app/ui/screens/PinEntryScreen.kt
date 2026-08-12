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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.ui.components.GhostButton
import com.squelch.app.ui.components.PrimaryButton
import com.squelch.app.ui.components.StatusCard
import com.squelch.app.ui.components.monoStyle

/** 6-digit PIN entry. Tapping each digit appends to the buffer; a DEL
 *  button pops. PIN is handed off via [onPinSubmit] when the buffer is
 *  full and the user taps UNLOCK. */
@Composable
fun PinEntryScreen(
    pinLength: Int,
    onPinSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    fun press(d: String) {
        digits = when (d) {
            "DEL" -> digits.dropLast(1)
            else -> if (digits.length < pinLength) digits + d else digits
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "ENTER MASTER PIN",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        )
        Text(
            "${pinLength} digits  -  used to derive the vault key",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11)
        )
        Spacer(Modifier.height(24.dp))

        StatusCard(title = "PIN") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(pinLength) { idx ->
                    val char = if (idx < digits.length) digits[idx] else '_'
                    Text(
                        text = "$char",
                        modifier = Modifier.width(28.dp),
                        color = if (idx < digits.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = monoStyle(22).copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clickable(enabled = digits.isNotEmpty()) { press("DEL") }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "[DEL]",
                        color = if (digits.isNotEmpty()) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (col in 1..3) {
                    val num = row * 3 + col
                    KeypadButton(num.toString()) { press(it) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        KeypadButton("0") { press(it) }

        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            label = "   UNLOCK   ",
            enabled = digits.length == pinLength
        ) { onPinSubmit(digits) }

        Spacer(Modifier.height(12.dp))

        GhostButton(label = "   CANCEL   ") { onCancel() }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick(label) },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary, style = monoStyle(18))
    }
}
