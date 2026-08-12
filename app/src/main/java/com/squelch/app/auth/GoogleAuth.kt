package com.squelch.app.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

/**
 * Thin wrapper around the Google Sign-In SDK. Hosts a single
 * [GoogleSignInClient] configured with the OAuth client id from
 * `R.string.google_client_id`, the Drive `drive.file` scope, and a
 * minimal set of identity scopes so we can read the Google `sub` (UID)
 * for the vault's Argon2id salt.
 *
 * Drive API access uses the Bearer token acquired from
 * `GoogleAuthUtil.getToken(...)` after sign-in. See [DriveRest].
 */
class GoogleAuth(private val context: Context) {

    private val webClientId: String =
        context.getString(com.squelch.app.R.string.google_client_id)

    val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestServerAuthCode(webClientId, false)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    /** Last cached account, used by the silent sign-in attempt. */
    val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(context)

    fun signInIntent(): Intent = signInClient.signInIntent

    suspend fun signOut() {
        signInClient.signOut()
    }
}
