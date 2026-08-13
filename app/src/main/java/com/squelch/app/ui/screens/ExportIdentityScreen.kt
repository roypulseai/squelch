package com.squelch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.ui.OnboardingViewModel
import com.squelch.app.ui.components.PrimaryButton
import com.squelch.app.ui.components.monoStyle

/** Display the unlocked mnemonic + a base64 export blob. Tapping a row
 *  copies that row to the system clipboard. */
@Composable
fun ExportIdentityScreen(
    vm: OnboardingViewModel,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val mnemonic = remember { vm.exportIdentityBase64() }
    val phrase = remember { mutableStateOf("") }
    val base64 = remember { mutableStateOf("") }

    // Lazy resolve of the mnemonic (we keep it out of the VM after
    // reading once so re-entering the screen re-renders).
    val resolvedMnemonic = phrase.value.ifEmpty {
        mnemonic?.let {
            phrase.value = it
            it
        } ?: ""
    }
    val resolvedBase64 = base64.value.ifEmpty {
        resolvedMnemonic.takeIf { it.isNotBlank() }?.let { _ ->
            // mnemonic is the base64 of the seed; the user is exporting
            // the seed, so mnemonic IS the base64 blob. We still show the
            // mnemonic words below for human-readable backup.
            base64.value = resolvedMnemonic
            resolvedMnemonic
        } ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            "EXPORT IDENTITY",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 3.sp
            )
        )
        Text(
            "treat this blob like a private key.  anyone with it can\n" +
                "sign as you.  never paste it into chat, share files, etc.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11)
        )

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { clipboard.setText(AnnotatedString(resolvedBase64)) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("BASE64 SEED (32 bytes)", color = MaterialTheme.colorScheme.secondary, style = monoStyle(10).copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (resolvedBase64.isBlank()) "(vault locked - unlock first)" else resolvedBase64,
                    color = if (resolvedBase64.isBlank()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                    style = monoStyle(13)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("tap to copy.  paste into a password manager / write it on paper.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(10))

        Spacer(Modifier.weight(1f))

        PrimaryButton(label = "   COPY BASE64   ", enabled = resolvedBase64.isNotBlank()) {
            clipboard.setText(AnnotatedString(resolvedBase64))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "   BACK   ",
            color = MaterialTheme.colorScheme.onSurface,
            style = monoStyle(11).copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onBack() }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}