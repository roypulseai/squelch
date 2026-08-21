package com.squelch.app.mesh.engine

import android.content.Context
import android.util.Log
import com.squelch.app.auth.AuthRepository
import com.squelch.app.crypto.Identity
import com.squelch.app.mesh.transport.BleTransport
import com.squelch.app.mesh.transport.FirestoreTransport
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.mesh.transport.WifiDirectTransport
import com.squelch.app.util.toHex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshEngineManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "MeshEngineManager"
    }

    @Volatile
    private var engine: MeshEngine? = null

    @Synchronized
    fun getOrCreate(): MeshEngine? {
        engine?.let { if (it.running) return it }
        return try {
            val googleUid = authRepository.signedIn()?.googleUid ?: return null
            val identity = Identity.fromGoogleUid(googleUid)
            val edPubHex = identity.edPub.toHex()

            val transports = mutableListOf<Transport>()
            transports.add(BleTransport(context, edPubHex))
            transports.add(WifiDirectTransport(context, edPubHex))

            val eng = MeshEngine(identity = identity, transports = transports)
            eng.start()
            engine = eng
            Log.d(TAG, "MeshEngine created and started")
            eng
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MeshEngine: ${e.message}", e)
            null
        }
    }

    fun get(): MeshEngine? = engine

    @Synchronized
    fun stop() {
        engine?.stop()
        engine = null
        Log.d(TAG, "MeshEngine stopped")
    }
}
