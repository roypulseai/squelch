package com.squelch.app.auth

import android.content.Context
import android.content.Intent
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
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.oauth_client_id))
        .requestServerAuthCode(context.getString(R.string.oauth_client_id))
        .requestEmail()
        .requestProfile()
        .build()

    private val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    fun signInIntent(): Intent = client.signInIntent

    fun getLastSignedInAccount() = GoogleSignIn.getLastSignedInAccount(context)

    fun signOut() = client.signOut()

    fun parseSignInResult(data: Intent?): AuthState {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            AuthState.SignedIn(
                email = account.email ?: "",
                googleUid = account.id ?: "",
                displayName = account.displayName ?: account.email ?: "Unknown",
                idToken = account.idToken ?: "",
                serverAuthCode = account.serverAuthCode
            )
        } catch (e: ApiException) {
            AuthState.Error("Sign-in failed (code ${e.statusCode}): ${e.message}")
        }
    }
}
