package com.squelch.app.data.repository

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.squelch.app.auth.AuthRepository
import com.squelch.app.auth.BiometricManager
import com.squelch.app.auth.BiometricVaultManager
import com.squelch.app.crypto.VaultCipher
import com.squelch.app.crypto.VaultPayload
import com.squelch.app.crypto.VaultSession
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.remote.FirestoreVaultManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val firestoreVaultManager: FirestoreVaultManager,
    private val biometricManager: BiometricManager,
    private val biometricVaultManager: BiometricVaultManager
) {
    companion object {
        private const val TAG = "VaultRepository"
    }

    sealed class VaultState {
        data object Idle : VaultState()
        data object Loading : VaultState()
        data object Unlocked : VaultState()
        data object BiometricRequired : VaultState()
        data class Error(val message: String) : VaultState()
    }

    private val _state = MutableStateFlow<VaultState>(VaultState.Idle)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private var database: SquelchDatabase? = null

    val db: SquelchDatabase? get() = database
    val isUnlocked: Boolean get() = VaultSession.isUnlocked
    val isLockEnabled: Boolean get() = biometricVaultManager.isLockEnabled()

    fun checkVaultState() {
        val signed = authRepository.signedIn() ?: return
        _state.value = VaultState.Loading

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val googleUid = signed.googleUid
                val hasVault = firestoreVaultManager.hasVault(googleUid)
                val lockEnabled = biometricVaultManager.isLockEnabled()

                if (!hasVault) {
                    provisionVault(googleUid)
                } else if (lockEnabled && biometricVaultManager.hasCachedKey()) {
                    _state.value = VaultState.BiometricRequired
                } else if (lockEnabled) {
                    _state.value = VaultState.BiometricRequired
                } else {
                    autoUnlock(googleUid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkVaultState failed: ${e.message}", e)
                _state.value = VaultState.Error(e.message ?: "Failed to load vault")
            }
        }
    }

    private fun provisionVault(googleUid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = VaultPayload()
                val ciphertext = VaultCipher.encryptVault(googleUid, payload)
                firestoreVaultManager.uploadVault(googleUid, ciphertext)

                val kDb = VaultCipher.deriveKDb(googleUid)
                VaultSession.unlock(kDb = kDb, googleUid = googleUid)
                database = SquelchDatabase.create(context, kDb)

                _state.value = VaultState.Unlocked
            } catch (e: Exception) {
                Log.e(TAG, "provisionVault failed: ${e.message}", e)
                _state.value = VaultState.Error("Setup failed: ${e.message}")
            }
        }
    }

    private fun autoUnlock(googleUid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val blob = firestoreVaultManager.downloadVault(googleUid)
                    ?: throw IllegalStateException("Vault not found")

                VaultCipher.decryptVault(googleUid, blob)
                val kDb = VaultCipher.deriveKDb(googleUid)
                VaultSession.unlock(kDb = kDb, googleUid = googleUid)
                database = SquelchDatabase.create(context, kDb)

                _state.value = VaultState.Unlocked
            } catch (e: Exception) {
                Log.e(TAG, "autoUnlock failed: ${e.message}", e)
                _state.value = VaultState.Error("Unlock failed: ${e.message}")
            }
        }
    }

    fun unlockWithBiometric(activity: FragmentActivity) {
        if (!biometricVaultManager.hasCachedKey()) {
            _state.value = VaultState.Error("No stored key. Restart the app.")
            return
        }

        try {
            val cipher = biometricVaultManager.getDecryptionCipher()
            biometricManager.authenticateWithCipher(
                activity = activity,
                cipher = cipher,
                title = "Unlock Vault",
                subtitle = "Verify your identity",
                onSuccess = { result ->
                    val resultCipher = result.cryptoObject?.cipher ?: return@authenticateWithCipher
                    try {
                        val kDb = biometricVaultManager.decryptKey(resultCipher)
                        val googleUid = authRepository.signedIn()?.googleUid ?: return@authenticateWithCipher
                        VaultSession.unlock(kDb = kDb, googleUid = googleUid)
                        database = SquelchDatabase.create(context, kDb)
                        _state.value = VaultState.Unlocked
                    } catch (e: Exception) {
                        Log.e(TAG, "decrypt kDb failed: ${e.message}", e)
                        _state.value = VaultState.Error("Unlock failed: ${e.message}")
                    }
                },
                onError = { msg ->
                    if (msg != "cancelled") {
                        _state.value = VaultState.Error(msg)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "biometric setup failed: ${e.message}", e)
            _state.value = VaultState.Error("Biometric not available: ${e.message}")
        }
    }

    fun enableBiometricLock(activity: FragmentActivity) {
        val kDb = VaultSession.kDbOrEmpty()
        if (kDb.isEmpty()) return

        try {
            val cipher = biometricVaultManager.getEncryptionCipher()
            biometricManager.authenticateWithCipher(
                activity = activity,
                cipher = cipher,
                title = "Enable Vault Lock",
                subtitle = "Authenticate to enable biometric lock",
                onSuccess = { result ->
                    val resultCipher = result.cryptoObject?.cipher ?: return@authenticateWithCipher
                    biometricVaultManager.saveEncryptedKey(resultCipher, kDb)
                    Log.d(TAG, "Biometric lock enabled")
                },
                onError = { msg ->
                    Log.w(TAG, "Enable lock cancelled: $msg")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "enableBiometricLock failed: ${e.message}")
        }
    }

    fun disableBiometricLock(activity: FragmentActivity) {
        try {
            val cipher = biometricVaultManager.getDecryptionCipher()
            biometricManager.authenticateWithCipher(
                activity = activity,
                cipher = cipher,
                title = "Disable Vault Lock",
                subtitle = "Authenticate to disable biometric lock",
                onSuccess = {
                    biometricVaultManager.clearLocalKey()
                    Log.d(TAG, "Biometric lock disabled")
                },
                onError = { msg ->
                    Log.w(TAG, "Disable lock cancelled: $msg")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "disableBiometricLock failed: ${e.message}")
        }
    }

    fun lock() {
        VaultSession.lock()
        database?.close()
        database = null
        _state.value = VaultState.BiometricRequired
    }

    fun signOut() {
        VaultSession.lock()
        database?.close()
        database = null
        biometricVaultManager.clearLocalKey()
        _state.value = VaultState.Idle
    }

    fun clearError() {
        if (_state.value is VaultState.Error) {
            _state.value = if (VaultSession.isUnlocked) VaultState.Unlocked else VaultState.BiometricRequired
        }
    }
}
