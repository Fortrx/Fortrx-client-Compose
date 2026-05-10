package com.fortrx.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

actual object NotificationBridge {
    private const val CHANNEL_ID = "fortrx_messages"
    private const val SUMMARY_NOTIFICATION_ID = 1001
    private const val MAX_LINES = 6
    private val recentMessages = ArrayDeque<String>()
    private var unreadCount = 0

    actual fun showIncomingMessage(title: String, body: String) {
        val context = runCatching { AndroidContextHolder.appContext }.getOrNull() ?: return
        ensureChannel(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val line = "$title: $body"
        synchronized(this) {
            unreadCount += 1
            recentMessages.addLast(line)
            while (recentMessages.size > MAX_LINES) {
                recentMessages.removeFirst()
            }
        }
        val lines = synchronized(this) { recentMessages.toList() }
        val count = synchronized(this) { unreadCount }
        val latestLine = lines.lastOrNull().orEmpty()
        val expandedBody = lines.joinToString(separator = "\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(
                if (count == 1) "Fortrx"
                else "Fortrx ($count new messages)"
            )
            .setContentText(latestLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Fortrx Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming message alerts"
            }
        )
    }
}
