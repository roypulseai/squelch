package com.squelch.p2p

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SquelchTheme {
                ScaffoldScreen()
            }
        }
    }
}

@Composable
private fun ScaffoldScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.scaffold_placeholder),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun SquelchTheme(content: @Composable () -> Unit) {
    // v2 will move to a phosphor-green retro theme like v1, but kept minimal
    // for the very first scaffold (dark + light text per platform defaults).
    androidx.compose.material3.MaterialTheme(content = content)
}
