package com.squelch.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.squelch.app.auth.AuthRepository
import com.squelch.app.data.remote.DriveBackupManager
import com.squelch.app.data.remote.FirestoreVaultManager
import com.squelch.app.data.repository.VaultRepository
import com.squelch.app.messaging.MessageRelayHolder
import com.squelch.app.mesh.relay.MessageRelay
import com.squelch.app.ui.navigation.AppEntry
import com.squelch.app.ui.theme.SquelchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        var pendingConversationId: String? = null
            private set
    }

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var vaultRepository: VaultRepository
    @Inject lateinit var driveBackupManager: DriveBackupManager
    @Inject lateinit var firestoreVaultManager: FirestoreVaultManager
    @Inject lateinit var messageRelay: MessageRelay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate start")
        handleIntent(intent)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    vaultRepository.lockForBackground()
                }
                else -> {}
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        try {
            setContent {
                SquelchTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppEntry(
                            authRepository = authRepository,
                            vaultRepository = vaultRepository,
                            driveBackupManager = driveBackupManager,
                            firestoreVaultManager = firestoreVaultManager,
                            messageRelay = messageRelay
                        )
                    }
                }
            }
            Log.d(TAG, "setContent completed")
        } catch (e: Exception) {
            Log.e(TAG, "setContent failed", e)
            try {
                val crashEntry = "\n--- ACTIVITY CRASH ---\n${Log.getStackTraceString(e)}\n"
                SquelchApp.crashLogFile?.appendText(crashEntry)
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val conversationId = intent?.getStringExtra("conversationId")
        if (conversationId != null) {
            pendingConversationId = conversationId
            Log.d(TAG, "Deep link to conversation: $conversationId")
        }
    }
}
