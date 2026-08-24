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
import com.squelch.app.mesh.engine.MeshEngineManager
import com.squelch.app.mesh.relay.MessageRelay
import com.squelch.app.translate.ModelPreloader
import com.squelch.app.ui.navigation.AppEntry
import com.squelch.app.ui.theme.SquelchTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private val _pendingConversationId = MutableStateFlow<String?>(null)
        val pendingConversationId: StateFlow<String?> = _pendingConversationId.asStateFlow()

        private val _pendingMarkRead = MutableStateFlow<String?>(null)
        val pendingMarkRead: StateFlow<String?> = _pendingMarkRead.asStateFlow()

        fun consumePendingConversationId() {
            _pendingConversationId.value = null
        }

        fun consumePendingMarkRead() {
            _pendingMarkRead.value = null
        }
    }

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var vaultRepository: VaultRepository
    @Inject lateinit var driveBackupManager: DriveBackupManager
    @Inject lateinit var firestoreVaultManager: FirestoreVaultManager
    @Inject lateinit var messageRelay: MessageRelay
    @Inject lateinit var meshEngineManager: MeshEngineManager
    @Inject lateinit var modelPreloader: ModelPreloader

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
                            messageRelay = messageRelay,
                            meshEngineManager = meshEngineManager,
                            modelPreloader = modelPreloader
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
            _pendingConversationId.value = conversationId
            Log.d(TAG, "Deep link to conversation: $conversationId")
        }
        val markRead = intent?.getBooleanExtra("markRead", false) ?: false
        if (markRead && conversationId != null) {
            _pendingMarkRead.value = conversationId
            Log.d(TAG, "Mark read for: $conversationId")
        }
    }
}
