package com.squelch.app.data.repository

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.squelch.app.auth.AuthRepository
import com.squelch.app.auth.BiometricManager
import com.squelch.app.auth.BiometricVaultManager
import com.squelch.app.crypto.Bip39
import com.squelch.app.crypto.VaultCipher
import com.squelch.app.crypto.VaultPayload
import com.squelch.app.crypto.VaultSession
import com.squelch.app.crypto.mnemonicToExportBlob
import com.squelch.app.data.local.SquelchDatabase
import com.squelch.app.data.remote.DriveVaultManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val driveVaultManager: DriveVaultManager,
    private val biometricManager: BiometricManager,
    private val biometricVaultManager: BiometricVaultManager
) {
    companion object {
        private const val TAG = "VaultRepository"
    }

    sealed class VaultState {
        data object Idle : VaultState()
        data object Probing : VaultState()
        data object Locked : VaultState()
        data object Provisioning : VaultState()
        data object MnemonicPending : VaultState()
        data class MnemonicBackup(val mnemonic: String) : VaultState()
        data object Encrypting : VaultState()
        data object Decrypting : VaultState()
        data object Unlocked : VaultState()
        data object BiometricRequired : VaultState()
        data object MnemonicRecovery : VaultState()
        data class Error(val message: String) : VaultState()
    }

    private val _state = MutableStateFlow<VaultState>(VaultState.Idle)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private var database: SquelchDatabase? = null
    private var pendingMnemonic: String? = null

    val db: SquelchDatabase? get() = database
    val isUnlocked: Boolean get() = VaultSession.isUnlocked

    fun initDrive() {
        val signed = authRepository.signedIn() ?: return
        driveVaultManager.init(signed)
    }

    fun checkVaultState() {
        val signed = authRepository.signedIn() ?: return
        _state.value = VaultState.Probing
        driveVaultManager.init(signed)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val folder = driveVaultManager.findOrCreateFolder()
                val existing = driveVaultManager.findVaultFile(folder.id)
                if (existing == null) {
                    _state.value = VaultState.Provisioning
                } else {
                    _state.value = VaultState.BiometricRequired
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkVaultState failed: ${e.message}", e)
                _state.value = VaultState.Error(e.message ?: "vault probe failed")
            }
        }
    }

    fun provisionWithBiometric(activity: FragmentActivity) {
        _state.value = VaultState.MnemonicPending
        CoroutineScope(Dispatchers.IO).launch {
            val mnemonic = withContext(Dispatchers.Default) {
                Bip39.generateMnemonic(context, entropyBytes = 32)
            }
            pendingMnemonic = mnemonic
            _state.value = VaultState.MnemonicBackup(mnemonic = mnemonic)
        }
    }

    fun provisionVault(mnemonic: String) {
        val signed = authRepository.signedIn() ?: return
        _state.value = VaultState.Encrypting

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val googleUid = signed.googleUid
                val kVault = VaultCipher.deriveKVault(mnemonic, googleUid)
                val kDb = VaultCipher.deriveKDb(kVault)
                val payload = VaultPayload(mnemonic = mnemonic)
                val ciphertext = VaultCipher.encryptVault(mnemonic, googleUid, payload)

                val folder = driveVaultManager.findOrCreateFolder()
                driveVaultManager.uploadVault(folderId = folder.id, bytes = ciphertext)

                VaultSession.unlock(mnemonic = mnemonic, kDb = kDb, googleUid = googleUid)
                database = SquelchDatabase.create(context, kDb)

                _state.value = VaultState.Unlocked
            } catch (e: Exception) {
                Log.e(TAG, "provisionVault failed: ${e.message}", e)
                _state.value = VaultState.Error(e.message ?: "provisioning failed")
            }
        }
    }

    fun unlockWithBiometric(activity: FragmentActivity) {
        if (!biometricVaultManager.hasCachedMnemonic()) {
            Log.d(TAG, "No local mnemonic cache, need recovery")
            _state.value = VaultState.MnemonicRecovery
            return
        }

        _state.value = VaultState.Decrypting

        try {
            val cipher = biometricVaultManager.getDecryptionCipher()
            biometricManager.authenticateWithCipher(
                activity = activity,
                cipher = cipher,
                title = "Unlock Vault",
                subtitle = "Verify your identity to unlock Squelch",
                onSuccess = { result ->
                    val resultCipher = result.cryptoObject?.cipher ?: return@authenticateWithCipher
                    try {
                        val mnemonic = biometricVaultManager.decryptMnemonic(resultCipher)
                        decryptAndUnlock(mnemonic)
                    } catch (e: Exception) {
                        Log.e(TAG, "decrypt mnemonic failed: ${e.message}", e)
                        _state.value = VaultState.Error("Failed to unlock: ${e.message}")
                    }
                },
                onError = { msg ->
                    if (msg == "cancelled") {
                        _state.value = VaultState.BiometricRequired
                    } else {
                        _state.value = VaultState.Error(msg)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "biometric setup failed: ${e.message}", e)
            _state.value = VaultState.MnemonicRecovery
        }
    }

    fun unlockWithMnemonic(mnemonic: String) {
        _state.value = VaultState.Decrypting
        decryptAndUnlock(mnemonic)
    }

    private fun decryptAndUnlock(mnemonic: String) {
        val signed = authRepository.signedIn() ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val googleUid = signed.googleUid
                val blob = driveVaultManager.downloadVault()
                    ?: throw IllegalStateException("no vault.enc found on Drive")

                val payload = VaultCipher.decryptVault(mnemonic, googleUid, blob)
                val kVault = VaultCipher.deriveKVault(mnemonic, googleUid)
                val kDb = VaultCipher.deriveKDb(kVault)

                VaultSession.unlock(mnemonic = payload.mnemonic, kDb = kDb, googleUid = googleUid)
                database = SquelchDatabase.create(context, kDb)

                _state.value = VaultState.Unlocked
            } catch (e: Exception) {
                Log.e(TAG, "decryptAndUnlock failed: ${e.message}", e)
                val msg = when {
                    e.message?.contains("AEADBadTagException") == true -> "Incorrect recovery phrase"
                    e.message?.contains("BadPaddingException") == true -> "Incorrect recovery phrase"
                    else -> e.message ?: "decryption failed"
                }
                _state.value = VaultState.Error(msg)
            }
        }
    }

    fun setupBiometricCache(activity: FragmentActivity, mnemonic: String) {
        try {
            val cipher = biometricVaultManager.getEncryptionCipher()
            biometricManager.authenticateWithCipher(
                activity = activity,
                cipher = cipher,
                title = "Save Recovery Key",
                subtitle = "Authenticate to save your recovery key on this device",
                onSuccess = { result ->
                    val resultCipher = result.cryptoObject?.cipher ?: return@authenticateWithCipher
                    biometricVaultManager.saveEncryptedMnemonic(resultCipher, mnemonic)
                    Log.d(TAG, "Mnemonic cached with biometric protection")
                },
                onError = { msg ->
                    Log.w(TAG, "Biometric cache skipped: $msg")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "setupBiometricCache failed: ${e.message}")
        }
    }

    fun lock() {
        VaultSession.lock()
        database?.close()
        database = null
        _state.value = VaultState.Locked
    }

    fun signOut() {
        VaultSession.lock()
        database?.close()
        database = null
        biometricVaultManager.clearLocalMnemonic()
        _state.value = VaultState.Idle
    }

    fun exportIdentity(): String? {
        val mn = VaultSession.mnemonicOrNull() ?: return null
        return mnemonicToExportBlob(mn)
    }

    fun clearError() {
        if (_state.value is VaultState.Error) {
            val wasLocked = VaultSession.isUnlocked.not()
            _state.value = if (wasLocked) VaultState.BiometricRequired else VaultState.Unlocked
        }
    }

    fun getPendingMnemonic(): String? = pendingMnemonic
}
