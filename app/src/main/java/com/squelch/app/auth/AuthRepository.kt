package com.squelch.app.auth

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuthManager: FirebaseAuthManager
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        if (firebaseAuthManager.isSignedIn) {
            val user = firebaseAuthManager.getCurrentFirebaseUser()
            if (user != null) {
                _state.value = AuthState.SignedIn(
                    email = user.email ?: "",
                    googleUid = user.uid,
                    displayName = user.displayName ?: user.email ?: "Unknown",
                    idToken = ""
                )
            }
        }
    }

    fun signInIntent(): Intent = firebaseAuthManager.getSignInIntent()

    fun onSignInResult(data: Intent?) {
        val result = firebaseAuthManager.parseSignInResult(data)
        _state.value = result
    }

    suspend fun completeFirebaseAuth(idToken: String): AuthState {
        val result = firebaseAuthManager.firebaseAuthWithGoogle(idToken)
        _state.value = result
        return result
    }

    fun signOut() {
        firebaseAuthManager.signOut()
        _state.value = AuthState.Idle
    }

    fun deleteAccount() {
        firebaseAuthManager.revokeAccess()
        _state.value = AuthState.Idle
    }

    fun signedIn(): AuthState.SignedIn? = _state.value as? AuthState.SignedIn
}
