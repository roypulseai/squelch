package com.squelch.app.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squelch.app.ui.theme.Accent

private data class PermissionItem(
    val permission: String,
    val label: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun PermissionsScreen(
    onAllGranted: () -> Unit,
    onSkip: () -> Unit
) {
    val permissions = buildList {
        add(PermissionItem(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            label = "Notifications",
            description = "Get alerts for new messages and mesh activity",
            icon = Icons.Default.Notifications
        ))
        add(PermissionItem(
            permission = Manifest.permission.BLUETOOTH_SCAN,
            label = "Bluetooth Scan",
            description = "Discover nearby peers for mesh networking",
            icon = Icons.Default.Bluetooth
        ))
        add(PermissionItem(
            permission = Manifest.permission.BLUETOOTH_ADVERTISE,
            label = "Bluetooth Advertise",
            description = "Allow other devices to find you on the mesh",
            icon = Icons.Default.Bluetooth
        ))
        add(PermissionItem(
            permission = Manifest.permission.BLUETOOTH_CONNECT,
            label = "Bluetooth Connect",
            description = "Connect to nearby peers for encrypted chat",
            icon = Icons.Default.Bluetooth
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermissionItem(
                permission = Manifest.permission.NEARBY_WIFI_DEVICES,
                label = "Nearby Wi-Fi",
                description = "Discover peers via Wi-Fi Direct for faster transfer",
                icon = Icons.Default.Wifi
            ))
        }
    }

    val grantedState = remember { mutableStateMapOf<String, Boolean>() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            grantedState[perm] = granted
        }
    }

    val allGranted = permissions.all { grantedState[it.permission] == true }

    LaunchedEffect(Unit) {
        val permsToRequest = permissions.map { it.permission }.toTypedArray()
        launcher.launch(permsToRequest)
    }

    LaunchedEffect(allGranted) {
        if (allGranted) {
            onAllGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = Accent.copy(alpha = 0.15f)
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(20.dp),
                tint = Accent
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Enable Mesh Features",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Squelch needs these permissions to discover nearby peers and deliver messages instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            permissions.forEach { item ->
                val isGranted = grantedState[item.permission] == true
                PermissionRow(
                    item = item,
                    isGranted = isGranted
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { launcher.launch(permissions.map { it.permission }.toTypedArray()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(
                text = if (allGranted) "All Permissions Granted" else "Grant Permissions",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onSkip) {
            Text(
                text = "Skip for now",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    isGranted: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isGranted) {
            Accent.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (isGranted) Accent.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else item.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .padding(9.dp),
                    tint = if (isGranted) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}
