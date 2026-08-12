package com.squelch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Card with a small label title followed by a region of free content. */
@Composable
fun StatusCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.secondary,
            style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** A bold primary action button. Dimmed variant when disabled. */
@Composable
fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .let { if (enabled) it.clickable { onClick() } else it },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            style = monoStyle(14).copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        )
    }
}

/** A subdued secondary action button (sign-out, cancel, etc.). */
@Composable
fun GhostButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(13)
        )
    }
}

/** Build a monospaced TextStyle sized at [size] dp. */
@Composable
fun monoStyle(size: Int): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp
    )

/** Numeric Dp helper. */
internal fun numDp(value: Int): Dp = androidx.compose.ui.unit.Dp(value.toFloat())
