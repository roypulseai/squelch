package com.squelch.app.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.squelch.app.R
import com.squelch.app.SquelchApp

/**
 * Foreground service that keeps the mesh radios alive while the app is backgrounded.
 * The engine itself lives in [SquelchApp]; the service only owns the notification
 * and the start/stop lifecycle.
 */
class MeshService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as SquelchApp
        when (intent?.action) {
            ACTION_STOP -> {
                app.stopMesh()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        app.startMesh()
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Squelch Mesh",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mesh radios are active"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_squelch)
            .setContentTitle("Squelch mesh active")
            .setContentText("Listening for nearby peers")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "squelch-mesh"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.squelch.app.mesh.START"
        private const val ACTION_STOP = "com.squelch.app.mesh.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MeshService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java).setAction(ACTION_STOP))
        }
    }
}
