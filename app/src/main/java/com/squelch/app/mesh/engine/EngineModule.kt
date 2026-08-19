package com.squelch.app.mesh.engine

import android.content.Context
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.VaultSession
import com.squelch.app.mesh.transport.BleTransport
import com.squelch.app.mesh.transport.FirestoreTransport
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.util.toHex
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideMeshEngine(
        @ApplicationContext context: Context
    ): MeshEngine? {
        val mn = VaultSession.mnemonicOrNull() ?: return null
        val identity = Identity.fromMnemonic(mn)

        val transports = mutableListOf<Transport>()

        // BLE mesh for nearby devices
        transports.add(BleTransport(context))

        // Firestore relay for internet chat
        transports.add(FirestoreTransport(edPubHex = identity.edPub.toHex()))

        return MeshEngine(identity = identity, transports = transports)
    }
}
