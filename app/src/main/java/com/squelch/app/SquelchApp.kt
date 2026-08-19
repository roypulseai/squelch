package com.squelch.app

import android.app.Application
import com.squelch.app.util.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SquelchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
