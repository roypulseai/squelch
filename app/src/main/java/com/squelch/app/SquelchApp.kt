package com.squelch.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.squelch.app.auth.AuthRepository
import com.squelch.app.data.remote.BackupWorker
import com.squelch.app.data.remote.DriveBackupManager
import com.squelch.app.data.remote.FirestoreVaultManager
import com.squelch.app.messaging.MessageForegroundService
import com.squelch.app.util.Notifications
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SquelchApp : Application(), Configuration.Provider {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var driveBackupManager: DriveBackupManager
    @Inject lateinit var firestoreVaultManager: FirestoreVaultManager

    private val workConfig: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(SquelchWorkerFactory(authRepository, driveBackupManager, firestoreVaultManager))
            .build()
    }

    override val workManagerConfiguration: Configuration get() = workConfig

    companion object {
        private const val TAG = "SquelchApp"
        var crashLogFile: File? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()

        crashLogFile = File(filesDir, "crash.log")

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val logEntry = "\n--- CRASH $timestamp [thread=${thread.name}] ---\n$sw\n"
                crashLogFile?.appendText(logEntry)
                Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            } catch (_: Exception) {}

            defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }

        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channels: ${e.message}", e)
        }

        scheduleMonthlyBackup()
    }

    private fun scheduleMonthlyBackup() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            30, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Monthly backup scheduled")
    }

    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
}

class SquelchWorkerFactory(
    private val authRepository: AuthRepository,
    private val driveBackupManager: DriveBackupManager,
    private val firestoreVaultManager: FirestoreVaultManager
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            "com.squelch.app.data.remote.BackupWorker" -> {
                BackupWorker(appContext, workerParameters, authRepository, driveBackupManager, firestoreVaultManager)
            }
            else -> null
        }
    }
}
