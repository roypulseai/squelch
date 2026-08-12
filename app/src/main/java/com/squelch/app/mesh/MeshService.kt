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

/**
 * Foreground service that hosts the [MeshEngine] for as long as the mesh
 * is active. Spec v0.5: foregroundServiceType="connectedDevice" since the
 * mesh uses BLE + WiFi Direct.
 *
 * START intent: starts the engine.
 * STOP intent: stops the engine and removes the notification.
 */
class MeshService : Service() {

    private val engine: MeshEngine by lazy { MeshEngine(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        engine.start()
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
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_squelch)
            .setContentTitle("Squelch mesh active")
            .setContentText("Listening for nearby peers via BLE + Wi-Fi Direct")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "squelch-mesh"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.squelch.app.mesh.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeshService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java).setAction(ACTION_STOP))
        }
    }
}
