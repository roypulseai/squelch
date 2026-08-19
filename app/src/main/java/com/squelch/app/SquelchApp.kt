package com.squelch.app

import android.app.Application
import android.util.Log
import com.squelch.app.util.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SquelchApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SquelchApp", "Uncaught exception in thread ${thread.name}", throwable)
            defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }

        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            Log.e("SquelchApp", "Failed to create notification channels: ${e.message}", e)
        }
    }

    private val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
}
