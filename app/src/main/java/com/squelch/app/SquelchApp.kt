package com.squelch.app

import android.app.Application
import android.util.Log
import com.squelch.app.util.Notifications
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class SquelchApp : Application() {
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
    }

    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
}
