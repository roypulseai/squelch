package com.squelch.app

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.squelch.app.auth.AuthRepository
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.ui.navigation.AppEntry
import com.squelch.app.ui.theme.SquelchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var vaultRepository: VaultRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate start")

        enableEdgeToEdge()

        try {
            setContent {
                SquelchTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppEntry(
                            authRepository = authRepository,
                            vaultRepository = vaultRepository
                        )
                    }
                }
            }
            Log.d(TAG, "setContent completed")
        } catch (e: Exception) {
            Log.e(TAG, "setContent failed", e)
        }
    }
}
