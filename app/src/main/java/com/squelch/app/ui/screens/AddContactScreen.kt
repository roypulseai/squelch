package com.squelch.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.squelch.app.db.Db
import com.squelch.app.mesh.TrustLevel
import com.squelch.app.qr.QrCodec
import com.squelch.app.qr.QrContact
import com.squelch.app.ui.components.monoStyle
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executors

/** Scan a contact QR via the back camera. The first decode is
 *  validated against the [QrContact] schema and upserted into the
 *  SQLCipher contacts table with trustLevel = MET. */
@Composable
fun AddContactScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var decoded by remember { mutableStateOf<QrContact.Contact?>(null) }
    var decodeError by remember { mutableStateOf<String?>(null) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val provider = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                val yPlane = proxy.planes[0]
                val buffer = yPlane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val text = QrCodec.decodeYuv(
                    yBytes = bytes,
                    width = proxy.width,
                    height = proxy.height,
                    rotationDegrees = proxy.imageInfo.rotationDegrees
                )
                if (text != null) {
                    val parsed = QrContact.decode(text)
                    if (parsed != null) {
                        val db = Db.instance
                        if (db != null) {
                            kotlinx.coroutines.runBlocking {
                                db.contacts().upsert(
                                    com.squelch.app.db.ContactEntity(
                                        pubkey = parsed.edPubHex,
                                        xPub = parsed.xPubHex,
                                        callsign = parsed.callsign,
                                        trustLevel = TrustLevel.MET,
                                        capabilities = 0,
                                        lastSeen = System.currentTimeMillis(),
                                        bluetoothAddress = "",
                                        mutualStatics = false,
                                        addedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                        decoded = parsed
                    } else {
                        decodeError = "QR decoded but not a Squelch contact"
                    }
                }
                proxy.close()
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (e: Exception) {
            decodeError = "camera bind failed: ${e.message}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Best-effort teardown so the camera doesn't stay hot.
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) { }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "ADD CONTACT",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 3.sp
            )
        )
        Text(
            "scan a Squelch contact QR (squelch://contact?...)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = monoStyle(11)
        )

        Spacer(Modifier.height(16.dp))

        when {
            !hasCameraPermission -> Text(
                "CAMERA permission denied. Grant it via Settings to scan.",
                color = MaterialTheme.colorScheme.error,
                style = monoStyle(13)
            )
            decoded != null -> {
                val c = decoded!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "ADDED",
                            color = MaterialTheme.colorScheme.primary,
                            style = monoStyle(11).copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("callsign : ${c.callsign}",
                            color = MaterialTheme.colorScheme.onSurface, style = monoStyle(13))
                        Text("ed pub   : ${c.edPubHex.take(20)}...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = monoStyle(11))
                        Text("x pub    : ${c.xPubHex.take(20)}...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = monoStyle(11))
                    }
                }
            }
            decodeError != null -> Text(
                "ERROR: ${decodeError}",
                color = MaterialTheme.colorScheme.error,
                style = monoStyle(12)
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (decoded != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                )
                .clickable { onBack() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (decoded != null) "   CONTINUE   " else "   BACK   ",
                color = if (decoded != null) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                style = monoStyle(13).copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}