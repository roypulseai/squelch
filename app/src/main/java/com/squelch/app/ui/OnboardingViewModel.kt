package com.squelch.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.squelch.app.auth.AuthState
import com.squelch.app.auth.AuthViewModel
import com.squelch.app.crypto.Bip39
import com.squelch.app.crypto.VaultCipher
import com.squelch.app.crypto.VaultPayload
import com.squelch.app.vault.DriveVaultManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI-side state for the onboarding screen: drives between AuthState transitions
 *  and the high-level "next step" Decision that MainActivity feeds to the nav. */
class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val authVm = AuthViewModel(app)
    val auth: StateFlow<AuthState> = authVm.state

    private val _driveStatus = MutableStateFlow<DriveStatus>(DriveStatus.Idle)
    val driveStatus: StateFlow<DriveStatus> = _driveStatus.asStateFlow()

    private val _cryptoTest = MutableStateFlow<CryptoTestResult>(CryptoTestResult.Idle)
    val cryptoTest: StateFlow<CryptoTestResult> = _cryptoTest.asStateFlow()

    /** Pass-through to the underlying auth view model. */
    fun signInIntent() = authVm.signInIntent()
    fun onSignInResult(task: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>) =
        authVm.onSignInResult(task)
    fun signOut() = authVm.signOut()

    /** After [AuthState.SignedIn], drive a single round-trip to confirm we
     *  can list/create /squelch/. Used as the smoke test for M2. */
    fun pingDrive() {
        viewModelScope.launch {
            val signed = authVm.state.value as? AuthState.SignedIn
            if (signed == null) {
                _driveStatus.value = DriveStatus.Error("Not signed in")
                return@launch
            }
            _driveStatus.value = DriveStatus.Loading
            try {
                val manager = DriveVaultManager(getApplication(), signed)
                val folder = manager.findOrCreateFolder()
                _driveStatus.value = DriveStatus.Ok(folder.id, folder.name)
            } catch (e: Exception) {
                _driveStatus.value = DriveStatus.Error(e.message ?: e::class.java.simpleName)
            }
        }
    }

    sealed class DriveStatus {
        data object Idle : DriveStatus()
        data object Loading : DriveStatus()
        data class Ok(val folderId: String, val folderName: String) : DriveStatus()
        data class Error(val message: String) : DriveStatus()
    }

    /** In-app crypto roundtrip result (verify Phase M3). */
    sealed class CryptoTestResult {
        data object Idle : CryptoTestResult()
        data object Running : CryptoTestResult()
        data class Ok(
            val mnemonic: String,
            val ciphertextBytes: Int,
            val decryptMatch: Boolean,
            val fpid: String
        ) : CryptoTestResult()
        data class Fail(val message: String) : CryptoTestResult()
    }

    /** Run a full BIP-39 + Argon2id + AES-GCM roundtrip on-device.
     *  Generate a 24-word mnemonic, build a VaultPayload, encrypt with
     *  the test PIN "123456" against a synthetic UID, decrypt, verify. */
    fun runCryptoRoundtrip(testPin: String = "123456", googleUid: String = "test-uid-roypulseai") {
        viewModelScope.launch {
            _cryptoTest.value = CryptoTestResult.Running
            try {
                val ctx = getApplication<Application>()
                val mnemonic = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    Bip39.generateMnemonic(ctx, entropyBytes = 32)
                }
                val payload = VaultPayload(mnemonic = mnemonic)
                val ciphertext = VaultCipher.encryptVault(testPin, googleUid, payload)
                val decrypted = VaultCipher.decryptVault(testPin, googleUid, ciphertext)
                val ok = decrypted.mnemonic == mnemonic &&
                        Bip39.validateMnemonic(ctx, mnemonic)
                val fingerprint = VaultCipher.vaultFingerprint(googleUid)
                _cryptoTest.value = CryptoTestResult.Ok(
                    mnemonic = mnemonic,
                    ciphertextBytes = ciphertext.size,
                    decryptMatch = ok,
                    fpid = fingerprint
                )
            } catch (e: Exception) {
                _cryptoTest.value = CryptoTestResult.Fail(e.message ?: e::class.java.simpleName)
            }
        }
    }
}
