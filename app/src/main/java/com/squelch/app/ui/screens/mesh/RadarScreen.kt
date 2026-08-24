package com.squelch.app.ui.screens.mesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.squelch.app.ui.theme.Accent
import com.squelch.app.ui.theme.AccentLight
import com.squelch.app.ui.theme.AccentDark
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val RadarGreen = Color(0xFF00A884)
private val RadarGreenDark = Color(0xFF005C4B)
private val RadarGreenGlow = Color(0xFF00FF88)
private val RadarRingColor = Color(0xFF1A3A2A)
private val BtBlue = Color(0xFF5B8DEF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    viewModel: RadarViewModel = hiltViewModel(),
    onUserTap: (String, String) -> Unit = { _, _ -> },
    onPeerTap: (String, String) -> Unit = { _, _ -> }
) {
    val transports by viewModel.transports.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val bleEnabled by viewModel.bleEnabled.collectAsState()
    val selfPubkey by viewModel.selfPubkey.collectAsState()
    val squelchUsers by viewModel.squelchUsers.collectAsState()

    val context = LocalContext.current
    val blePermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    fun hasBlePermissions(): Boolean = blePermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.toggleBle()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Radar", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.scanNow() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Scan",
                    modifier = Modifier.rotate(if (isScanning) 360f else 0f)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
        ) {
            item {
                RadarVisualization(
                    peerCount = peers.size,
                    isScanning = isScanning,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                NetworkStatsRow(stats = stats)
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Transport Layers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(transports, key = { it.name }) { transport ->
                TransportCard(
                    transport = transport,
                    onToggle = if (transport.name == "Bluetooth LE") {
                        {
                            if (!bleEnabled && !hasBlePermissions()) {
                                blePermissionLauncher.launch(blePermissions)
                            } else {
                                viewModel.toggleBle()
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Discovered Peers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${peers.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (peers.isEmpty()) {
                item {
                    EmptyPeersCard(isScanning = isScanning)
                }
            } else {
                items(peers, key = { it.id }) { peer ->
                    PeerCard(
                        peer = peer,
                        onTap = {
                            if (peer.isSquelchUser || peer.isContact) {
                                onPeerTap(peer.id, peer.name)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Squelch Users",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RadarGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${squelchUsers.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = RadarGreen
                        )
                    }
                }
            }

            if (squelchUsers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No users found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Other Squelch users will appear here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(squelchUsers, key = { it.uid }) { user ->
                    SquelchUserCard(
                        user = user,
                        onTap = {
                            onUserTap(user.edPub, user.displayName)
                        }
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Your Identity",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(RadarGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selfPubkey.ifEmpty { "Not initialized" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Uptime: ${formatUptime(stats.uptimeMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarVisualization(
    peerCount: Int,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val scanScale by animateFloatAsState(
        targetValue = if (isScanning) 1.05f else 1f,
        animationSpec = tween(300),
        label = "scan"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .size(240.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D2818),
                        Color(0xFF061208),
                        Color(0xFF030908)
                    )
                )
            )
            .border(2.dp, RadarGreen.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = min(size.width, size.height) / 2

            // Concentric rings
            for (i in 1..4) {
                val ringRadius = maxRadius * (i / 4f)
                drawCircle(
                    color = RadarRingColor,
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }

            // Cross lines
            drawLine(
                color = RadarRingColor,
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 0.5f
            )
            drawLine(
                color = RadarRingColor,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 0.5f
            )

            // Diagonal lines
            drawLine(
                color = RadarRingColor.copy(alpha = 0.5f),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = 0.5f
            )
            drawLine(
                color = RadarRingColor.copy(alpha = 0.5f),
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 0.5f
            )

            // Sweep arc
            val sweepRadius = maxRadius * 0.95f
            val startAngle = Math.toRadians(sweepAngle.toDouble()).toFloat()

            val sweepPath = Path().apply {
                moveTo(center.x, center.y)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        center.x - sweepRadius,
                        center.y - sweepRadius,
                        center.x + sweepRadius,
                        center.y + sweepRadius
                    ),
                    startAngleDegrees = sweepAngle,
                    sweepAngleDegrees = 45f,
                    forceMoveTo = true
                )
                close()
            }

            drawPath(
                path = sweepPath,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        RadarGreenGlow.copy(alpha = 0.25f),
                        RadarGreen.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center
                )
            )

            // Bright sweep line
            val lineEndX = center.x + sweepRadius * cos(Math.toRadians(sweepAngle.toDouble())).toFloat()
            val lineEndY = center.y + sweepRadius * sin(Math.toRadians(sweepAngle.toDouble())).toFloat()
            drawLine(
                color = RadarGreenGlow.copy(alpha = pulseAlpha * 0.8f),
                start = center,
                end = Offset(lineEndX, lineEndY),
                strokeWidth = 2f
            )

            // Peer dots
            val peerPositions = listOf(
                Pair(0.65f, 30f),
                Pair(0.45f, 120f),
                Pair(0.8f, 200f),
                Pair(0.3f, 310f),
                Pair(0.55f, 60f),
                Pair(0.7f, 160f),
                Pair(0.35f, 250f),
                Pair(0.5f, 340f)
            )

            for (i in 0 until minOf(peerCount, peerPositions.size)) {
                val (dist, angleDeg) = peerPositions[i]
                val radius = maxRadius * dist
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val dotX = center.x + radius * cos(angleRad).toFloat()
                val dotY = center.y + radius * sin(angleRad).toFloat()

                val dotAlpha = if (isScanning) pulseAlpha else 0.8f

                drawCircle(
                    color = RadarGreenGlow.copy(alpha = dotAlpha * 0.3f),
                    radius = 12f,
                    center = Offset(dotX, dotY)
                )
                drawCircle(
                    color = RadarGreen,
                    radius = 5f,
                    center = Offset(dotX, dotY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2f,
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Center dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RadarGreen)
                .border(1.dp, Color.White, CircleShape)
        )

        // Peer count overlay
        if (peerCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = RadarGreenDark.copy(alpha = 0.9f)
            ) {
                Text(
                    text = "$peerCount peers",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NetworkStatsRow(stats: RadarViewModel.NetworkStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip(
            label = "Total",
            value = "${stats.totalPeers}",
            color = RadarGreen,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = "BLE",
            value = "${stats.blePeers}",
            color = BtBlue,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = "Uptime",
            value = formatUptime(stats.uptimeMs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun TransportCard(
    transport: RadarViewModel.TransportStatus,
    onToggle: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (transport.isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (transport.isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = transport.icon,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transport.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (transport.isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RadarGreen.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "LIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = RadarGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = transport.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (transport.peerCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${transport.peerCount} peer${if (transport.peerCount != 1) "s" else ""} connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (onToggle != null) {
                Switch(
                    checked = transport.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RadarGreen
                    )
                )
            }
        }
    }
}

@Composable
private fun PeerCard(peer: RadarViewModel.PeerInfo, onTap: () -> Unit = {}) {
    val canChat = peer.isSquelchUser || peer.isContact
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canChat) Modifier.clickable { onTap() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BtBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BtBlue
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (peer.isContact) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RadarGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "CONTACT",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = RadarGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (peer.isSquelchUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RadarGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "SQUELCH",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = RadarGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BtBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = peer.transport,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = BtBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Signal strength indicator
            SignalBars(strength = peer.signalStrength)
        }
    }
}

@Composable
private fun SquelchUserCard(
    user: RadarViewModel.SquelchUser,
    onTap: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RadarGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                    color = RadarGreen,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (user.isContact) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RadarGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "CONTACT",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = RadarGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val username = user.userId.ifEmpty { user.email.substringBefore("@") }
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SignalBars(strength: Int) {
    val barColor = when {
        strength > 75 -> RadarGreen
        strength > 50 -> Color(0xFFE5C357)
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier.height(20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..4) {
            val barHeight = (4 + i * 4).dp
            val isActive = strength > (i * 20)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (isActive) barColor
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
private fun EmptyPeersCard(isScanning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isScanning) "Scanning..." else "No peers found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isScanning) "Looking for nearby devices"
                else "Enable Bluetooth to discover peers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatUptime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 60000) % 60
    val hours = ms / 3600000
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
