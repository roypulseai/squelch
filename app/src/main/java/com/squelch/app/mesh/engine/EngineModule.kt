package com.squelch.app.mesh.engine

import android.content.Context
import com.squelch.app.auth.AuthRepository
import com.squelch.app.crypto.Identity
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
        @ApplicationContext context: Context,
        authRepository: AuthRepository
    ): MeshEngine? {
        val googleUid = authRepository.signedIn()?.googleUid ?: return null
        val identity = Identity.fromGoogleUid(googleUid)

        val transports = mutableListOf<Transport>()
        transports.add(BleTransport(context))
        transports.add(FirestoreTransport(edPubHex = identity.edPub.toHex()))

        return MeshEngine(identity = identity, transports = transports)
    }
}
