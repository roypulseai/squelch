package com.squelch.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.R
import com.squelch.app.ui.OnboardingViewModel
import com.squelch.app.ui.components.PrimaryButton
import com.squelch.app.ui.components.StatusCard
import com.squelch.app.ui.components.monoStyle

@Composable
fun SignedInStub(
    vm: OnboardingViewModel,
    email: String,
    uid: String,
    onSignOut: () -> Unit
) {
    val crypto by vm.cryptoTest.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
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
                        fontSize = 22.sp,
                        letterSpacing = 3.sp
                    )
                )
                Text(
                    text = "p2p  ·  serverless",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = monoStyle(11)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        StatusCard(title = "SIGNED IN") {
            Text("email : $email", color = MaterialTheme.colorScheme.onSurface, style = monoStyle(13))
            Text("uid   : ${uid.take(20)}…", color = MaterialTheme.colorScheme.onSurface, style = monoStyle(13))
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "NEXT",
            color = MaterialTheme.colorScheme.secondary,
            style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(6.dp))

        NumberRow("01", "Enter your 6-digit Master PIN.")
        NumberRow("02", "Type or paste your 24-word Mnemonic recovery phrase.")

        Spacer(Modifier.height(20.dp))

        StatusCard(title = "DEV: VAULT CIPHER (M3 roundtrip)") {
            val current = crypto
            val color = when (current) {
                is OnboardingViewModel.CryptoTestResult.Ok ->
                    if (current.decryptMatch) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                is OnboardingViewModel.CryptoTestResult.Fail -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = cryptoText(current),
                color = color,
                style = monoStyle(12)
            )
        }

        Spacer(Modifier.height(8.dp))

        PrimaryButton(
            label = when (crypto) {
                OnboardingViewModel.CryptoTestResult.Running -> "   WORKING…   "
                else -> "   TEST VAULT ROUNDTRIP   "
            },
            enabled = crypto !is OnboardingViewModel.CryptoTestResult.Running
        ) {
            vm.runCryptoRoundtrip()
        }

        Spacer(Modifier.height(20.dp))

        StatusCard(title = "WHAT THIS ENABLES") {
            Text(
                text = "•  identity & contacts encrypted with your PIN + Argon2id\n" +
                        "•  vault.enc only in your /squelch/ Drive folder\n" +
                        "•  QR-code contact exchange (coming)\n" +
                        "•  offline BLE / WiFi mesh fallback (coming)",
                color = MaterialTheme.colorScheme.onSurface,
                style = monoStyle(12)
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "v0.4.0  ·  M3",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = monoStyle(10)
            )
            Text(
                text = "   SIGN OUT   ",
                color = MaterialTheme.colorScheme.error,
                style = monoStyle(11).copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onSignOut() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NumberRow(num: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = num,
                color = MaterialTheme.colorScheme.onPrimary,
                style = monoStyle(10).copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurface,
            style = monoStyle(13)
        )
    }
    Spacer(Modifier.height(8.dp))
}

private fun cryptoText(crypto: OnboardingViewModel.CryptoTestResult): String = when (crypto) {
    OnboardingViewModel.CryptoTestResult.Idle ->
        "tap TEST VAULT ROUNDTRIP to verify\nBIP-39 + Argon2id + AES-256-GCM\nin this build."
    OnboardingViewModel.CryptoTestResult.Running ->
        "(running…)"
    is OnboardingViewModel.CryptoTestResult.Ok ->
        "mnemonic[:3]   : ${crypto.mnemonic.split(" ").take(3).joinToString(" ")}…\n" +
        "ciphertext     : ${crypto.ciphertextBytes} bytes\n" +
        "roundtrip ok   : ${if (crypto.decryptMatch) "YES" else "NO"}\n" +
        "vault fp       : ${crypto.fpid}\n" +
        "(full mnemonic NOT shown here on purpose)"
    is OnboardingViewModel.CryptoTestResult.Fail ->
        "ERROR: ${crypto.message}"
}
