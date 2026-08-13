package com.squelch.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.crypto.VaultSession
import com.squelch.app.qr.QrCodec
import com.squelch.app.qr.QrContact
import com.squelch.app.ui.components.monoStyle

/** Display my contact QR. The bitmap is regenerated every time the
 *  unlocked identity changes. We do NOT cache the bytes long-term;
 *  the QR only contains the public half of the contact. */
@Composable
fun MyQrScreen(
    onBack: () -> Unit
) {
    val mnemonic = VaultSession.mnemonicOrNull()
    val payload = remember(mnemonic) {
        if (mnemonic == null) null else {
            val id = com.squelch.app.crypto.Identity.fromMnemonic(mnemonic)
            val callsign = com.squelch.app.mesh.Hello.callsignFor(id.edPub, id.xPub)
            val text = QrContact.encode(
                QrContact.Contact(
                    edPubHex = com.squelch.app.util.Bytes.hex(id.edPub),
                    xPubHex = com.squelch.app.util.Bytes.hex(id.xPub),
                    callsign = callsign,
                    trustLevel = 1
                )
            )
            text to QrCodec.encode(text)
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
            "MY CONTACT",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 3.sp
            )
        )
        Text(
            "have someone scan this with Squelch -> Add Contact",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11)
        )

        Spacer(Modifier.height(20.dp))

        if (payload == null) {
            Text(
                "(vault locked - unlock first)",
                color = MaterialTheme.colorScheme.error,
                style = monoStyle(13)
            )
        } else {
            val (url, bitmap) = payload
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(8.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "my contact QR",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = url,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = monoStyle(9)
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            "   BACK   ",
            color = MaterialTheme.colorScheme.onSurface,
            style = monoStyle(12).copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onBack() }
                .padding(vertical = 10.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}