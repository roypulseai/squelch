package com.squelch.app.auth

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val signInManager: GoogleSignInManager
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun signInIntent(): Intent = signInManager.signInIntent()

    fun onSignInResult(data: Intent?) {
        _state.value = signInManager.parseSignInResult(data)
    }

    fun signOut() {
        signInManager.signOut()
        _state.value = AuthState.Idle
    }

    fun currentState(): AuthState = _state.value

    fun signedIn(): AuthState.SignedIn? = _state.value as? AuthState.SignedIn
}
