package com.squelch.app.crypto

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/** Loads or creates the device identity on first launch (spec M0). */
class IdentityManager(context: Context) {
    private val store = KeyStoreManager(context.applicationContext)
    private val random = SecureRandom()

    @Volatile
    var identity: Identity = loadOrCreate()
        private set

    val callsign: String by lazy { Callsign.fromFingerprint(identity.fingerprint) }

    private fun loadOrCreate(): Identity {
        store.loadBlob()?.let { blob ->
            return try {
                Identity.fromBlob(blob)
            } catch (e: Exception) {
                createAndSave()
            }
        }
        return createAndSave()
    }

    private fun createAndSave(): Identity {
        val id = Identity(Ed25519.generateSeed(random), X25519.generateSecret(random))
        store.saveBlob(id.toBlob())
        return id
    }

    fun regenerate() {
        identity = createAndSave()
    }

    fun exportBase64(): String = Base64.encodeToString(identity.toBlob(), Base64.NO_WRAP)

    fun importBase64(encoded: String): Boolean {
        return try {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val id = Identity.fromBlob(blob)
            store.saveBlob(id.toBlob())
            identity = id
            true
        } catch (e: Exception) {
            false
        }
    }

    fun wipe() {
        store.wipeBlob()
        identity = createAndSave()
    }
}
