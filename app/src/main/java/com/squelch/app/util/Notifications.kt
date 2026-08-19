package com.squelch.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.squelch.app.MainActivity
import com.squelch.app.R

object Notifications {
    const val CHANNEL_MESSAGES = "squelch_messages"
    const val CHANNEL_SERVICE = "squelch_service"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming chat messages"
            enableVibration(true)
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Mesh Service",
            NotificationManager.IMPORTANCE_LOW
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(notificationId, notification)
    }
}
