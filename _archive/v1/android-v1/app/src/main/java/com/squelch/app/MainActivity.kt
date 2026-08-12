package com.squelch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.squelch.app.ui.LocalActivity
import com.squelch.app.ui.SquelchRoot
import com.squelch.app.ui.theme.SquelchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SquelchTheme {
                CompositionLocalProvider(LocalActivity provides this) {
                    SquelchRoot()
                }
            }
        }
    }
}
