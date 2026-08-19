package com.squelch.app.mesh.engine

import android.content.Context
import com.squelch.app.auth.AuthRepository
import com.squelch.app.crypto.Identity
import com.squelch.app.crypto.VaultSession
import com.squelch.app.mesh.transport.BleTransport
import com.squelch.app.mesh.transport.Transport
import com.squelch.app.mesh.transport.WebSocketTransport
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
        @ApplicationContext context: Context,
        authRepository: AuthRepository
    ): MeshEngine? {
        val mn = VaultSession.mnemonicOrNull() ?: return null
        val identity = Identity.fromMnemonic(mn)

        val transports = mutableListOf<Transport>()
        transports.add(BleTransport(context))

        val signed = authRepository.signedIn()
        if (signed != null && signed.idToken.isNotEmpty()) {
            val wsTransport = WebSocketTransport(
                relayUrl = "wss://relay.squelch.app/v2/mesh",
                authToken = signed.idToken,
                edPubHex = identity.edPub.toHex()
            )
            transports.add(wsTransport)
        }

        return MeshEngine(identity = identity, transports = transports)
    }
}
