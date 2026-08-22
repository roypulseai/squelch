package com.squelch.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.squelch.app.MainActivity
import com.squelch.app.R

object Notifications {
    const val CHANNEL_MESSAGES = "squelch_messages"
    const val CHANNEL_SERVICE = "squelch_service"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val prefs = context.getSharedPreferences("notif_channel_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("channel_recreated_v3", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.deleteNotificationChannel(CHANNEL_MESSAGES)
                nm.deleteNotificationChannel(CHANNEL_SERVICE)
            }
            prefs.edit().putBoolean("channel_recreated_v3", true).apply()
        }

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming chat messages"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150, 100, 150)
            enableLights(true)
            lightColor = 0xFF00D4AA.toInt()
            setSound(defaultSoundUri, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Mesh Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Background mesh connectivity"
            setShowBadge(false)
        }

        nm.createNotificationChannels(listOf(messagesChannel, serviceChannel))
    }

    fun showMessageNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        conversationId: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
            putExtra("markRead", true)
        }
        val markReadPending = PendingIntent.getActivity(
            context, notificationId + 10000, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 150, 100, 150))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(conversationId)
            .setShortcutId(conversationId)
            .setLocalOnly(true)
            .addAction(0, "Mark read", markReadPending)
            .build()

        nm.notify(notificationId, notification)
    }
}
