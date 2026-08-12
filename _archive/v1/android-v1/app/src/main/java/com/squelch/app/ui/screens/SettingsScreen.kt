package com.squelch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squelch.app.mesh.MeshEngine
import com.squelch.app.ui.InfoRow
import com.squelch.app.ui.StatusBar

@Composable
fun SettingsScreen(
    meshStatus: MeshEngine.MeshStatus,
    myCallsign: String,
    myFingerprint: String,
    meshRunning: Boolean,
    onToggleMesh: () -> Unit,
    onExport: () -> String,
    onImport: (String) -> Boolean,
    onPurge: (Long) -> Unit
) {
    var exportShown by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        StatusBar("SETTINGS")

        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("IDENTITY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            InfoRow("callsign", myCallsign)
            InfoRow("fingerprint", myFingerprint)

            Row {
                Text(
                    text = if (exportShown) "[HIDE ID]" else "[EXPORT ID]",
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .clickable { exportShown = !exportShown }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (exportShown) {
                Text(
                    onExport(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("import base64 id", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                textStyle = MaterialTheme.typography.labelMedium
            )
            Row {
                Text(
                    text = "[IMPORT]",
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .clickable {
                            importResult = if (onImport(importText.trim())) "> IMPORTED. NEW IDENTITY." else "> IMPORT FAILED."
                            importText = ""
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (importResult.isNotEmpty()) {
                Text(importResult, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }

            Spacer(Modifier.width(1.dp))

            Text("MESH", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            InfoRow("engine", if (meshRunning) "RUNNING" else "STOPPED")
            InfoRow("links", meshStatus.links.toString())
            InfoRow("store+forward", meshStatus.storeForwarded.toString())
            Row {
                Text(
                    text = if (meshRunning) "[STOP MESH]" else "[START MESH]",
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .clickable { onToggleMesh() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.width(1.dp))

            Text("STORAGE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "[PURGE >1H]",
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .clickable { onPurge(1) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "[PURGE >24H]",
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .clickable { onPurge(24) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.width(1.dp))
            Text(
                "SQUELCH 0.1.0  |  BLE + NFC + WiFi P2P\nTTL 6 hops  |  Noise XX  |  Ed25519 + X25519",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
