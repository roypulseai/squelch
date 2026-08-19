package com.squelch.app.auth

sealed class AuthState {
    data object Idle : AuthState()
    data object SigningIn : AuthState()
    data class PendingGoogleAuth(val idToken: String) : AuthState()
    data class SignedIn(
        val email: String,
        val googleUid: String,
        val displayName: String,
        val idToken: String
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}
