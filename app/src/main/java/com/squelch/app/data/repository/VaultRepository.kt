package com.squelch.app.data.repository

import android.content.Context
import com.squelch.app.auth.AuthRepository
import com.squelch.app.crypto.Bip39
import com.squelch.app.crypto.VaultCipher
import com.squelch.app.crypto.VaultOps
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
    private val driveVaultManager: DriveVaultManager
) {
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
        data class Error(val message: String) : VaultState()
    }

    private val _state = MutableStateFlow<VaultState>(VaultState.Idle)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private var database: SquelchDatabase? = null

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
                _state.value = if (existing == null) VaultState.Provisioning else VaultState.Locked
            } catch (e: Exception) {
                _state.value = VaultState.Error(e.message ?: "vault probe failed")
            }
        }
    }

    fun generateMnemonic() {
        _state.value = VaultState.MnemonicPending
        CoroutineScope(Dispatchers.IO).launch {
            val mnemonic = withContext(Dispatchers.Default) {
                Bip39.generateMnemonic(context, entropyBytes = 32)
            }
            _state.value = VaultState.MnemonicBackup(mnemonic = mnemonic)
        }
    }

    fun provisionVault(pin: String, mnemonic: String) {
        val signed = authRepository.signedIn() ?: return
        _state.value = VaultState.Encrypting

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val googleUid = signed.googleUid
                val kVault = VaultCipher.deriveKVault(pin, googleUid)
                val kDb = VaultCipher.deriveKDb(kVault)
                val payload = VaultPayload(mnemonic = mnemonic)
                val ciphertext = VaultCipher.encryptVault(pin, googleUid, payload)

                val folder = driveVaultManager.findOrCreateFolder()
                driveVaultManager.uploadVault(folderId = folder.id, bytes = ciphertext)

                VaultSession.unlock(mnemonic = mnemonic, kDb = kDb, googleUid = googleUid)
                database = SquelchDatabase.create(context, kDb)

                _state.value = VaultState.Unlocked
            } catch (e: Exception) {
                _state.value = VaultState.Error(e.message ?: "provisioning failed")
            }
        }
    }

    fun unlockWithPin(pin: String) {
        val signed = authRepository.signedIn() ?: return
        _state.value = VaultState.Decrypting

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val googleUid = signed.googleUid
                val blob = driveVaultManager.downloadVault()
                    ?: throw IllegalStateException("no vault.enc found")
                val payload = VaultCipher.decryptVault(pin, googleUid, blob)
                val kVault = VaultCipher.deriveKVault(pin, googleUid)
                val kDb = VaultCipher.deriveKDb(kVault)

                VaultSession.unlock(mnemonic = payload.mnemonic, kDb = kDb, googleUid = googleUid)
                database = SquelchDatabase.create(context, kDb)

                _state.value = VaultState.Unlocked
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("Failed to authenticate") == true -> "wrong PIN"
                    e.message?.contains("BadPaddingException") == true -> "wrong PIN"
                    e.message?.contains("AEADBadTagException") == true -> "wrong PIN"
                    else -> e.message ?: "decryption failed"
                }
                _state.value = VaultState.Error(msg)
            }
        }
    }

    fun changePin(oldPin: String, newPin: String) {
        val signed = authRepository.signedIn() ?: return
        _state.value = VaultState.Encrypting

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val googleUid = signed.googleUid
                val blob = driveVaultManager.downloadVault()
                    ?: throw IllegalStateException("no vault on Drive")

                val result = VaultOps.preparePinRotation(oldPin, newPin, googleUid, blob)
                val folder = driveVaultManager.findOrCreateFolder()
                driveVaultManager.uploadVault(folderId = folder.id, bytes = result.newCiphertext)

                database?.close()
                database = null
                VaultSession.unlock(mnemonic = result.newMnemonic, kDb = result.newKDb, googleUid = googleUid)
                database = SquelchDatabase.create(context, result.newKDb)

                _state.value = VaultState.Unlocked
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("Failed to authenticate") == true -> "wrong current PIN"
                    e.message?.contains("AEADBadTagException") == true -> "wrong current PIN"
                    else -> e.message ?: "rotation failed"
                }
                _state.value = VaultState.Error(msg)
            }
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
        _state.value = VaultState.Idle
    }

    fun exportIdentity(): String? {
        val mn = VaultSession.mnemonicOrNull() ?: return null
        return mnemonicToExportBlob(mn)
    }

    fun clearError() {
        if (_state.value is VaultState.Error) {
            val wasLocked = VaultSession.isUnlocked.not()
            _state.value = if (wasLocked) VaultState.Locked else VaultState.Unlocked
        }
    }
}
