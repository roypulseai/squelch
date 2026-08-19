package com.squelch.app.auth

import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.squelch.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class FirebaseAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FirebaseAuthManager"
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid
    val isSignedIn: Boolean get() = auth.currentUser != null
    fun getCurrentFirebaseUser() = auth.currentUser

    fun getSignInIntent(): Intent {
        val webClientId = context.getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    suspend fun firebaseAuthWithGoogle(idToken: String): AuthState {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = suspendCancellableCoroutine { cont ->
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        cont.resume(task)
                    }
            }
            val user = result.result?.user
            if (user != null) {
                AuthState.SignedIn(
                    email = user.email ?: "",
                    googleUid = user.uid,
                    displayName = user.displayName ?: user.email ?: "Unknown",
                    idToken = idToken
                )
            } else {
                AuthState.Error("Sign-in failed: no user")
            }
        } catch (e: Exception) {
            Log.e(TAG, "firebaseAuthWithGoogle failed: ${e.message}", e)
            AuthState.Error("Sign-in failed: ${e.message}")
        }
    }

    fun parseSignInResult(data: Intent?): AuthState {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                AuthState.PendingGoogleAuth(idToken)
            } else {
                AuthState.Error("No ID token received")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed: code=${e.statusCode}", e)
            AuthState.Error("Sign-in failed (code ${e.statusCode})")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in parse failed", e)
            AuthState.Error("Sign-in failed: ${e.message}")
        }
    }

    fun signOut() {
        auth.signOut()
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        ).signOut()
    }
}
