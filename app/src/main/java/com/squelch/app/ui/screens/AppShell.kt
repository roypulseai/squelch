package com.squelch.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * The main UI once the vault is unlocked. M6 finishes with the call-sign
 * + fingerprint display plus Lock Now / Sign Out. M7 layers the chat,
 * contacts, and radar surfaces here.
 */
@Composable
fun AppShell(vm: OnboardingViewModel) {
    val fp = VaultSession.kDbOrEmpty().take(4).joinToString("") { "%02x".format(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "SQUELCH",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        letterSpacing = 4.sp
                    )
                )
                Text(
                    "p2p  -  serverless  -  unlocked",
                    color = MaterialTheme.colorScheme.primary,
                    style = monoStyle(11)
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            "VAULT UNLOCKED",
            color = MaterialTheme.colorScheme.secondary,
            style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "db fp    : $fp...",
            color = MaterialTheme.colorScheme.onSurface,
            style = monoStyle(13)
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "NEXT (in M7)",
            color = MaterialTheme.colorScheme.secondary,
            style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text("- Chat surface with the discovered peers", color = MaterialTheme.colorScheme.onSurface, style = monoStyle(11))
        Text("- Contacts with QR-code exchange", color = MaterialTheme.colorScheme.onSurface, style = monoStyle(11))
        Text("- Settings: change PIN, lock now, export", color = MaterialTheme.colorScheme.onSurface, style = monoStyle(11))

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            label = "   LOCK NOW   ",
            enabled = true,
            onClick = { vm.lock() }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "   SIGN OUT   ",
            color = MaterialTheme.colorScheme.error,
            style = monoStyle(12).copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { vm.signOut() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "v0.6.0  -  M6 ready",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(10)
        )
        Spacer(Modifier.height(20.dp))
    }
}
