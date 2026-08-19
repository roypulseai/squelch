package com.squelch.app.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.squelch.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignInManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GoogleSignInManager"
    }

    private val client: GoogleSignInClient? by lazy {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.oauth_client_id))
                .requestServerAuthCode(context.getString(R.string.oauth_client_id))
                .requestEmail()
                .requestProfile()
                .build()
            GoogleSignIn.getClient(context, gso)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create GoogleSignInClient: ${e.message}", e)
            null
        }
    }

    fun signInIntent(): Intent {
        return client?.signInIntent
            ?: Intent().apply {
                putExtra("error", "Google Sign-In not configured")
            }
    }

    fun getLastSignedInAccount() = try {
        GoogleSignIn.getLastSignedInAccount(context)
    } catch (e: Exception) {
        Log.e(TAG, "getLastSignedInAccount failed: ${e.message}")
        null
    }

    fun signOut() {
        try {
            client?.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "signOut failed: ${e.message}")
        }
    }

    fun parseSignInResult(data: Intent?): AuthState {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            AuthState.SignedIn(
                email = account.email ?: "",
                googleUid = account.id ?: "",
                displayName = account.displayName ?: account.email ?: "Unknown",
                idToken = account.idToken ?: "",
                serverAuthCode = account.serverAuthCode
            )
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed: code=${e.statusCode}", e)
            AuthState.Error("Sign-in failed (code ${e.statusCode}): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in parse failed", e)
            AuthState.Error("Sign-in failed: ${e.message}")
        }
    }
}
