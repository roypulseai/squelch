package com.squelch.app.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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

private const val PREFS_NAME = "permissions_prefs"
private const val KEY_ASKED = "permissions_asked"

fun hasPermissionsAsked(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ASKED, false)
}

fun markPermissionsAsked(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_ASKED, true).apply()
}

@Composable
fun PermissionsScreen(
    onAllGranted: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

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

    val permissionState = remember { mutableStateMapOf<String, Boolean?>() }
    var requestCounter by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            permissionState[perm] = granted
        }
        requestCounter++
    }

    val decidedCount = permissionState.count { it.value != null }
    val allDecided = decidedCount == permissions.size
    val allGranted = permissions.all { permissionState[it.permission] == true }

    val undecidedPermissions = remember(decidedCount, requestCounter) {
        permissions.filter { permissionState[it.permission] == null }
    }

    LaunchedEffect(Unit) {
        markPermissionsAsked(context)
    }

    LaunchedEffect(allDecided, allGranted) {
        if (allDecided && allGranted) {
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
            text = if (allDecided) {
                if (allGranted) "All permissions granted!" else "Some permissions were denied. You can still use Squelch."
            } else {
                "Squelch needs these permissions to discover nearby peers and deliver messages instantly."
            },
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
                val isGranted = permissionState[item.permission] == true
                val isDenied = permissionState[item.permission] == false
                PermissionRow(
                    item = item,
                    isGranted = isGranted,
                    isDenied = isDenied
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!allDecided) {
            Button(
                onClick = {
                    val permsToRequest = undecidedPermissions.map { it.permission }.toTypedArray()
                    launcher.launch(permsToRequest)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(
                    text = "Grant Permissions",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = {
                permissions.forEach { permissionState[it.permission] = false }
            }) {
                Text(
                    text = "Deny All",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Button(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(
                    text = "Continue",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    isGranted: Boolean,
    isDenied: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isGranted -> Accent.copy(alpha = 0.08f)
            isDenied -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                color = when {
                    isGranted -> Accent.copy(alpha = 0.2f)
                    isDenied -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ) {
                Icon(
                    imageVector = when {
                        isGranted -> Icons.Default.Check
                        isDenied -> Icons.Default.Close
                        else -> item.icon
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .padding(9.dp),
                    tint = when {
                        isGranted -> Accent
                        isDenied -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
                    text = when {
                        isGranted -> "Granted"
                        isDenied -> "Denied"
                        else -> item.description
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isGranted -> Accent
                        isDenied -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp
                )
            }
        }
    }
}
