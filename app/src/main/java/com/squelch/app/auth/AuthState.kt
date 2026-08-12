package com.squelch.app.auth

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException

/** Single observable auth state. */
sealed class AuthState {
    data object Idle : AuthState()
    data object SigningIn : AuthState()
    data class SignedIn(
        val email: String,
        val googleUid: String,
        val displayName: String?,
        val idToken: String?,
        val serverAuthCode: String?
    ) : AuthState()
    data class Error(val message: String) : AuthState()

    companion object {
        fun fromAccount(account: GoogleSignInAccount?): SignedIn? {
            if (account == null) return null
            return SignedIn(
                email = account.email.orEmpty(),
                googleUid = account.id.orEmpty(),
                displayName = account.displayName,
                idToken = account.idToken,
                serverAuthCode = account.serverAuthCode
            )
        }

        fun fromException(exception: Exception): Error {
            val msg = if (exception is ApiException) {
                "Sign-in failed (status=${exception.statusCode})"
            } else {
                exception.message ?: exception::class.java.simpleName
            }
            return Error(msg)
        }
    }
}
