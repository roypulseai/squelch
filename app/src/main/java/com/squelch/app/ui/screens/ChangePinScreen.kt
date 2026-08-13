package com.squelch.app.ui.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.ui.OnboardingViewModel
import com.squelch.app.ui.components.PrimaryButton
import com.squelch.app.ui.components.StatusCard
import com.squelch.app.ui.components.monoStyle

/** Two-stage PIN update: enter the current PIN once, the new PIN twice.
 *  The vault is re-encrypted and the SQLCipher DB is re-opened. */
@Composable
fun ChangePinScreen(
    vm: OnboardingViewModel,
    pinLength: Int,
    onBack: () -> Unit
) {
    val rotation by vm.pinRotation.collectAsState()

    var step by remember { mutableStateOf(0) } // 0 = current, 1 = new, 2 = confirm
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    fun reset() { step = 0; currentPin = ""; newPin = ""; confirmPin = "" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            "CHANGE PIN",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 3.sp
            )
        )
        Spacer(Modifier.height(20.dp))

        StatusCard(title = if (step == 0) "ENTER CURRENT PIN" else "ENTER NEW PIN") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(pinLength) { idx ->
                    val char = if (idx < currentPin.length) currentPin[idx] else '_'
                    Text(
                        text = "$char",
                        modifier = Modifier.width(28.dp),
                        color = if (idx < currentPin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = monoStyle(22).copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clickable(enabled = currentPin.isNotEmpty()) { currentPin = currentPin.dropLast(1) }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("[DEL]", color = MaterialTheme.colorScheme.secondary, style = monoStyle(11))
                }
            }
        }
        if (step == 1) {
            Spacer(Modifier.height(8.dp))
            StatusCard(title = "CONFIRM NEW PIN") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(pinLength) { idx ->
                        val char = if (idx < newPin.length) newPin[idx] else '_'
                        Text(
                            text = "$char",
                            modifier = Modifier.width(28.dp),
                            color = if (idx < newPin.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = monoStyle(22).copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clickable(enabled = newPin.isNotEmpty()) { newPin = newPin.dropLast(1) }
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("[DEL]", color = MaterialTheme.colorScheme.secondary, style = monoStyle(11))
                    }
                }
            }
        }

        // Progress label
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (step) {
                0 -> "step 1/3   enter current PIN"
                1 -> "step 2/3   enter + confirm new PIN"
                else -> "rotating..."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11)
        )

        // Result + status
        Spacer(Modifier.height(12.dp))
        when (val r = rotation) {
            is OnboardingViewModel.PinRotation.Error ->
                Text(
                    "ERROR: ${r.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = monoStyle(12)
                )
            is OnboardingViewModel.PinRotation.Done ->
                Text(
                    "PIN rotated. New PIN is active.",
                    color = MaterialTheme.colorScheme.primary,
                    style = monoStyle(12)
                )
            else -> Unit
        }

        // Numeric pad (writes to the active buffer)
        Spacer(Modifier.weight(1f))

        val activeBuffer = when (step) {
            0 -> { v: String -> currentPin = v }
            1 -> { v: String -> newPin = v }
            else -> null
        }
        val currentValue = when (step) {
            0 -> currentPin
            else -> newPin
        }
        fun press(d: String) {
            if (activeBuffer == null) return
            currentValue.let { cv ->
                val next = when (d) {
                    "DEL" -> cv.dropLast(1)
                    else -> if (cv.length < pinLength) cv + d else cv
                }
                if (step == 0) currentPin = next else newPin = next
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(3) { row ->
                Row {
                    for (col in 1..3) {
                        val num = row * 3 + col
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable(enabled = activeBuffer != null) { press(num.toString()) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(num.toString(), color = MaterialTheme.colorScheme.primary, style = monoStyle(20))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = activeBuffer != null) { press("0") },
                contentAlignment = Alignment.Center
            ) {
                Text("0", color = MaterialTheme.colorScheme.primary, style = monoStyle(20))
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            label = when {
                rotation is OnboardingViewModel.PinRotation.Running -> "   ROTATING…   "
                step == 0 -> "   NEXT: NEW PIN   "
                step == 1 -> "   CONFIRM NEW PIN   "
                else -> "   DONE   "
            },
            enabled = when {
                rotation is OnboardingViewModel.PinRotation.Running -> false
                step == 0 -> currentPin.length == pinLength
                step == 1 -> newPin.length == pinLength && newPin == confirmPin
                else -> false
            }
        ) {
            when (step) {
                0 -> if (currentPin.length == pinLength) step = 1
                1 -> if (newPin.length == pinLength && newPin == confirmPin) {
                    vm.rotatePin(oldPin = currentPin, newPin = newPin)
                    step = 2
                }
                else -> onBack()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "   CANCEL   ",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11).copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clickable { reset(); vm.clearPinRotationResult(); onBack() }
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}