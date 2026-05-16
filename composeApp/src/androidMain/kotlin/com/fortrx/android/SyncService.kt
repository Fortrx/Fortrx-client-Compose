package com.fortrx.android

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fortrx.FortrxClient
import com.fortrx.storage.SettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncService : Service(), KoinComponent {
    private val fortrxClient: FortrxClient by inject()
    private val CHANNEL_ID = "fortrx_sync_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            fortrxClient.stopSyncEngine()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val password = SettingsStore.loadStoragePassword()

        if (password != null) {
            startForeground(NOTIFICATION_ID, createNotification())
            if (!fortrxClient.isSyncRunning()) {
                fortrxClient.startSyncEngine(password)
            }
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fortrx")
            .setContentText("Connected")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Fortrx Sync Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
