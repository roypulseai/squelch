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
import com.squelch.app.crypto.VaultSession
import com.squelch.app.db.AppDatabase
import com.squelch.app.mesh.MeshEngine
import com.squelch.app.mesh.MeshService
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

    private val meshEngine by lazy { MeshEngine(getApplication()) }
    val meshStatus get() = meshEngine.status
    val meshPeers get() = meshEngine.peers
    val meshMessages get() = meshEngine.messages

    /** M6: the unlock pipeline. */
    private val _vaultFlow = MutableStateFlow<VaultFlowState>(VaultFlowState.Idle)
    val vaultFlow: StateFlow<VaultFlowState> = _vaultFlow.asStateFlow()

    /** M6: surface the (provisioned-but-locked) state after sign-in. */
    val vaultRequiresSetup: StateFlow<Boolean> = MutableStateFlow(false)

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

    /** Toggle the offline mesh engine + foreground service. */
    fun toggleMesh() {
        if (meshStatus.value.running) {
            meshEngine.stop()
            MeshService.stop(getApplication())
        } else {
            meshEngine.start()
            MeshService.start(getApplication())
        }
    }

    /** Send a plaintext chat via the mesh engine. Look up the peer's
     *  edPub via persisted contacts (first match by endpoint name prefix)
     *  and route through Noise if a session is up. */
    fun sendToPeerEndpoint(peerEndpointId: String, text: String) {
        // For M7 we accept that we haven't yet bridged endpointId -> edPub
        // for sending. The helper is here to keep the UI wiring clean;
        // a follow-on commit ships the routing logic once Noise sessions
        // are observeable from the engine. Tracked as M7.1.
    }

    /* ---------- M6: vault onboarding flow ---------- */

    /** Walks the Drive folder, then either asks the user to PIN-unlock or
     *  generates a fresh mnemonic. Called from the post-sign-in Stub. */
    fun checkVaultState() {
        viewModelScope.launch {
            val signed = authVm.state.value as? AuthState.SignedIn ?: return@launch
            _vaultFlow.value = VaultFlowState.Probing
            try {
                val manager = DriveVaultManager(getApplication(), signed)
                val folder = manager.findOrCreateFolder()
                val existing = manager.findVaultFile(folder.id)
                _vaultFlow.value = if (existing == null) {
                    VaultFlowState.Provisioning
                } else {
                    VaultFlowState.Locked
                }
            } catch (e: Exception) {
                _vaultFlow.value = VaultFlowState.Error(
                    message = e.message ?: "vault probe failed"
                )
            }
        }
    }

    /** Generate a new 24-word mnemonic locally, populate the in-memory state, hand it to the backup screen. */
    fun generateMnemonicAndPreview() {
        viewModelScope.launch {
            _vaultFlow.value = VaultFlowState.MnemonicPending
            val ctx = getApplication<Application>()
            val mnemonic = withContext(kotlinx.coroutines.Dispatchers.Default) {
                Bip39.generateMnemonic(ctx, entropyBytes = 32)
            }
            _vaultFlow.value = VaultFlowState.MnemonicBackup(mnemonic = mnemonic)
        }
    }

/** Called when the user confirms they've written the mnemonic down. Encrypts
     *  the payload with the PIN and uploads vault.enc to /squelch/. */
    fun provisionVault(googleUid: String, pin: String, mnemonic: String) {
        viewModelScope.launch {
            val signed = authVm.state.value as? AuthState.SignedIn ?: return@launch
            _vaultFlow.value = VaultFlowState.Encrypting
            try {
                val kVault = VaultCipher.deriveKVault(pin, googleUid)
                val kDb = VaultCipher.deriveKDb(kVault)
                val ciphertext = VaultCipher.encryptVault(
                    pin = pin,
                    googleUid = googleUid,
                    payload = VaultPayload(mnemonic = mnemonic)
                )
                val manager = DriveVaultManager(getApplication(), signed)
                val folder = manager.findOrCreateFolder()
                manager.uploadVault(folderId = folder.id, bytes = ciphertext)
                VaultSession.unlock(mnemonic = mnemonic, kDb = kDb, googleUid = googleUid)
                AppDatabase.openFromSession(getApplication())?.let { db ->
                    com.squelch.app.db.Db.instance = db
                    meshEngine.rebuildIdentityIfPossible()
                }
                _vaultFlow.value = VaultFlowState.Unlocked
            } catch (e: Exception) {
                _vaultFlow.value = VaultFlowState.Error(
                    message = e.message ?: "encryption/upload failed"
                )
            }
        }
    }

    /** Called when the user enters their PIN. Downloads vault.enc, decrypts. */
    fun unlockWithPin(googleUid: String, pin: String) {
        viewModelScope.launch {
            val signed = authVm.state.value as? AuthState.SignedIn ?: return@launch
            _vaultFlow.value = VaultFlowState.Decrypting
            try {
                val manager = DriveVaultManager(getApplication(), signed)
                val blob = manager.downloadVault() ?: throw IllegalStateException(
                    "no vault.enc found - run setup first"
                )
                val payload = VaultCipher.decryptVault(pin, googleUid, blob)
                val kVault = VaultCipher.deriveKVault(pin, googleUid)
                val kDb = VaultCipher.deriveKDb(kVault)
                VaultSession.unlock(
                    mnemonic = payload.mnemonic,
                    kDb = kDb,
                    googleUid = googleUid
                )
                AppDatabase.openFromSession(getApplication())?.let { db ->
                    com.squelch.app.db.Db.instance = db
                    meshEngine.rebuildIdentityIfPossible()
                }
                _vaultFlow.value = VaultFlowState.Unlocked
            } catch (e: Exception) {
                _vaultFlow.value = VaultFlowState.Error(
                    message = (e.message ?: "decryption failed").let {
                        when {
                            it.contains("Failed to authenticate") -> "wrong PIN"
                            it.contains("BadPaddingException") -> "wrong PIN"
                            it.contains("AEADBadTagException") -> "wrong PIN"
                            else -> it
                        }
                    }
                )
            }
        }
    }

    /** Manually lock (Settings -> Lock). */
    fun lock() {
        VaultSession.lock()
        AppDatabase.close()
        _vaultFlow.value = VaultFlowState.Locked
    }

    sealed class VaultFlowState {
        data object Idle : VaultFlowState()
        data object Probing : VaultFlowState()
        data object Locked : VaultFlowState()             // vault exists; awaiting PIN
        data object Provisioning : VaultFlowState()       // no vault yet; next step is mnemonic gen
        data object MnemonicPending : VaultFlowState()
        data class MnemonicBackup(val mnemonic: String) : VaultFlowState()
        data object Encrypting : VaultFlowState()
        data object Decrypting : VaultFlowState()
        data object Unlocked : VaultFlowState()
        data class Error(val message: String) : VaultFlowState()
    }

    /** Run a BIP-39 + Argon2id + AES-GCM roundtrip on-device. */
    fun runCryptoRoundtrip(
        testPin: String = "123456",
        googleUid: String = "test-uid-roypulseai"
    ) {
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
                _cryptoTest.value = CryptoTestResult.Fail(
                    e.message ?: e::class.java.simpleName
                )
            }
        }
    }
}
