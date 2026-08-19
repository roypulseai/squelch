package com.squelch.app.di

import android.content.Context
import com.squelch.app.auth.AuthRepository
import com.squelch.app.auth.BiometricManager
import com.squelch.app.auth.BiometricVaultManager
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
    fun provideBiometricManager(@ApplicationContext context: Context): BiometricManager {
        return BiometricManager(context)
    }

    @Provides
    @Singleton
    fun provideBiometricVaultManager(@ApplicationContext context: Context): BiometricVaultManager {
        return BiometricVaultManager(context)
    }

    @Provides
    @Singleton
    fun provideVaultRepository(
        @ApplicationContext context: Context,
        authRepository: AuthRepository,
        driveVaultManager: DriveVaultManager,
        biometricManager: BiometricManager,
        biometricVaultManager: BiometricVaultManager
    ): VaultRepository {
        return VaultRepository(context, authRepository, driveVaultManager, biometricManager, biometricVaultManager)
    }
}
