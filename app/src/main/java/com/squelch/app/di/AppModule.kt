package com.squelch.app.di

import android.content.Context
import com.squelch.app.auth.AuthRepository
import com.squelch.app.auth.GoogleSignInManager
import com.squelch.app.data.remote.DriveVaultManager
import com.squelch.app.data.repository.VaultRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGoogleSignInManager(@ApplicationContext context: Context): GoogleSignInManager {
        return GoogleSignInManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(signInManager: GoogleSignInManager): AuthRepository {
        return AuthRepository(signInManager)
    }

    @Provides
    @Singleton
    fun provideDriveVaultManager(): DriveVaultManager {
        return DriveVaultManager()
    }

    @Provides
    @Singleton
    fun provideVaultRepository(
        @ApplicationContext context: Context,
        authRepository: AuthRepository,
        driveVaultManager: DriveVaultManager
    ): VaultRepository {
        return VaultRepository(context, authRepository, driveVaultManager)
    }
}
