package com.squelch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.squelch.app.db.RoomEntity
import com.squelch.app.mesh.MeshEngine
import com.squelch.app.ui.StatusBar
import com.squelch.app.ui.hhmm

@Composable
fun RoomsScreen(
    rooms: List<RoomEntity>,
    meshStatus: MeshEngine.MeshStatus,
    onJoin: (String, String) -> Unit,
    onLeave: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatusBar("ROOMS  |  ${rooms.count { it.joined }} open")
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("/join name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("passphrase", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "[GO]",
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    .clickable {
                        if (name.isNotBlank()) {
                            onJoin(name.trim(), pass)
                            name = ""
                            pass = ""
                        }
                    }
                    .align(Alignment.CenterVertically)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (rooms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "> NO ROOMS. /JOIN ONE.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Column
        }
        LazyColumn {
            items(rooms.filter { it.joined }) { room ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(room.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "#", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(room.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("joined ${hhmm(room.createdAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "[LEAVE]",
                        modifier = Modifier
                            .clickable { onLeave(room.id) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
