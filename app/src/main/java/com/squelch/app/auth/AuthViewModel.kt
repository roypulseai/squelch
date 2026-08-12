package com.squelch.app.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owns the user-visible [AuthState] for the v2 onboarding flow. */
class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = GoogleAuth(app)

    private val _state = MutableStateFlow<AuthState>(
        AuthState.fromAccount(auth.lastSignedInAccount) ?: AuthState.Idle
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun signInIntent() = auth.signInIntent()

    /** Handle the result of the [signInIntent] launched from the activity. */
    fun onSignInResult(task: Task<GoogleSignInAccount>) {
        _state.value = AuthState.SigningIn
        try {
            val account = task.getResult(ApiException::class.java)
            val signed = AuthState.fromAccount(account)
            _state.value = signed ?: AuthState.Error("Account unavailable")
        } catch (e: ApiException) {
            _state.value = AuthState.fromException(e)
        } catch (e: Exception) {
            _state.value = AuthState.fromException(e)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _state.value = AuthState.Idle
        }
    }

    fun reset() {
        _state.value = AuthState.Idle
    }
}
