package com.squelch.app.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.ContextCompat
import com.squelch.app.ui.theme.Accent

private data class PermissionItem(
    val permission: String,
    val label: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val minSdk: Int = 0
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

private fun isGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun PermissionsScreen(
    onAllGranted: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionItem(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    label = "Notifications",
                    description = "Get alerts for new messages",
                    icon = Icons.Default.Notifications,
                    minSdk = Build.VERSION_CODES.TIRAMISU
                ))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(PermissionItem(
                    permission = Manifest.permission.BLUETOOTH_SCAN,
                    label = "Bluetooth Scan",
                    description = "Discover nearby peers for mesh networking",
                    icon = Icons.Default.Bluetooth,
                    minSdk = Build.VERSION_CODES.S
                ))
                add(PermissionItem(
                    permission = Manifest.permission.BLUETOOTH_ADVERTISE,
                    label = "Bluetooth Advertise",
                    description = "Let other devices find you on the mesh",
                    icon = Icons.Default.Bluetooth,
                    minSdk = Build.VERSION_CODES.S
                ))
                add(PermissionItem(
                    permission = Manifest.permission.BLUETOOTH_CONNECT,
                    label = "Bluetooth Connect",
                    description = "Connect to nearby peers for encrypted chat",
                    icon = Icons.Default.Bluetooth,
                    minSdk = Build.VERSION_CODES.S
                ))
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                add(PermissionItem(
                    permission = Manifest.permission.ACCESS_FINE_LOCATION,
                    label = "Location",
                    description = "Required for Bluetooth scanning on Android 11 and below",
                    icon = Icons.Default.LocationOn,
                    minSdk = 0
                ))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionItem(
                    permission = Manifest.permission.NEARBY_WIFI_DEVICES,
                    label = "Nearby Wi-Fi",
                    description = "Discover peers via Wi-Fi Direct",
                    icon = Icons.Default.Wifi,
                    minSdk = Build.VERSION_CODES.TIRAMISU
                ))
            }
        }
    }

    val permissionState = remember { mutableStateMapOf<String, Boolean?>() }
    var requestCounter by remember { mutableIntStateOf(0) }
    var hasInteracted by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        permissions.forEach { item ->
            val granted = isGranted(context, item.permission)
            permissionState[item.permission] = if (granted) true else null
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            permissionState[perm] = granted
        }
        requestCounter++
        hasInteracted = 1
    }

    val decidedCount = permissionState.count { it.value != null }
    val allDecided = decidedCount == permissions.size
    val allGranted = permissions.all { permissionState[it.permission] == true }
    val anyPermanentlyDenied = permissions.any { item ->
        permissionState[item.permission] == false &&
        !isGranted(context, item.permission) &&
        hasInteracted > 0
    }

    val undecidedPermissions = remember(requestCounter) {
        permissions.filter { permissionState[it.permission] == null }
    }

    LaunchedEffect(allDecided, allGranted) {
        if (allDecided && allGranted && hasInteracted > 0) {
            markPermissionsAsked(context)
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
                if (allGranted) "All permissions granted!" else "Some permissions were denied. You can still use Squelch, but mesh features may not work."
            } else {
                "Squelch needs these permissions to discover nearby peers and deliver messages."
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

        if (anyPermanentlyDenied) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open App Settings", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!allDecided) {
            Button(
                onClick = {
                    val permsToRequest = undecidedPermissions.map { it.permission }.toTypedArray()
                    if (permsToRequest.isNotEmpty()) {
                        launcher.launch(permsToRequest)
                    }
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
                hasInteracted = 1
            }) {
                Text(
                    text = "Deny All",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Button(
                onClick = {
                    markPermissionsAsked(context)
                    onSkip()
                },
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
