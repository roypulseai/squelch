package com.squelch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Shown immediately after the user signs in successfully. The PIN entry /
 *  mnemonic backup flow lives here in M6. For M2, this is a placeholder that
 *  confirms routing and surfaces the email + uid so the developer can see the
 *  sign-in result on-device. */
@Composable
fun SignedInStub(
    email: String,
    uid: String,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(60.dp))

            Text(
                text = "SQUELCH",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = "SIGNED IN",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "email  : $email",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "uid    : ${uid.take(16)}…",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = "NEXT  >>  enter your 6-digit Master PIN.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "  >>  type the 24-word Mnemonic recovery phrase.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = "[ SIGN OUT ]",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickable { onSignOut() }
            )
        }
    }
}
