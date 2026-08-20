package com.squelch.app.data.remote

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.squelch.app.auth.AuthRepository
import com.squelch.app.crypto.VaultSession
import com.squelch.app.data.repository.VaultRepository

class BackupWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val driveBackupManager: DriveBackupManager,
    private val firestoreVaultManager: FirestoreVaultManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BackupWorker"
        const val WORK_NAME = "squelch_monthly_backup"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting monthly auto-backup")

        val signedIn = authRepository.signedIn()
        if (signedIn == null) {
            Log.i(TAG, "User not signed in, skipping backup")
            return Result.success()
        }

        if (!VaultSession.isUnlocked) {
            Log.i(TAG, "Vault is locked, skipping backup")
            return Result.success()
        }

        val googleUid = signedIn.googleUid

        return try {
            val vaultBlob = firestoreVaultManager.downloadVault(googleUid)
            if (vaultBlob == null) {
                Log.e(TAG, "Failed to download vault from Firestore")
                return Result.retry()
            }

            val dbFile = applicationContext.getDatabasePath("squelch.db")
            if (!dbFile.exists()) {
                Log.e(TAG, "Database file not found at ${dbFile.absolutePath}")
                return Result.retry()
            }

            if (!driveBackupManager.ensureDriveAccess()) {
                Log.w(TAG, "Drive access unavailable, retrying later")
                return Result.retry()
            }

            val vaultUploaded = driveBackupManager.backupVault(vaultBlob)
            if (!vaultUploaded) {
                Log.e(TAG, "Failed to upload vault to Drive")
                return Result.retry()
            }

            val dbUploaded = driveBackupManager.backupMessages(dbFile)
            if (!dbUploaded) {
                Log.e(TAG, "Failed to upload database to Drive")
                return Result.retry()
            }

            Log.i(TAG, "Monthly backup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed with exception", e)
            Result.retry()
        }
    }
}
