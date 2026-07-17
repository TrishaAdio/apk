package com.upimonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Lightweight foreground service. Its only job is to keep the process alive and
 * whitelisted so the manifest SMS receiver stays reliable under aggressive
 * battery management. It shows a persistent, low-priority notification.
 */
class ForwardService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("UPI Monitor active")
            .setContentText("Listening for UPI payment SMS")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "UPI Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "upi_monitor_fg"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, ForwardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ForwardService::class.java))
        }
    }
}
