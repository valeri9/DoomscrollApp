package com.valeri.doomscroll.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Exists only to keep this process's priority elevated for as long as an intervention
 * overlay is on screen — typically 10 to 45+ seconds, occasionally longer with night-mode
 * escalation.
 *
 * The overlay window being visually on top of everything does not, by itself, raise the
 * hosting process's priority in Android's own model: only a resumed Activity or an active
 * foreground service does, and this app deliberately has neither running most of the time.
 * On One UI, the background process freezer (Freecess) can suspend the whole process —
 * every thread, every coroutine, not just this one — while the overlay is showing, which is
 * what silently freezes the breathing countdown until you touch the screen and the process
 * gets woken back up to handle the input.
 *
 * Started right before the window is added, stopped right after it's removed. There is no
 * work to do here beyond holding the foreground state, so onStartCommand does nothing that
 * onCreate hasn't already done.
 */
class InterventionForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doomscroll")
            .setContentText("Pausing you for a moment")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "intervention"
        private const val NOTIFICATION_ID = 4200

        fun start(context: Context) {
            context.startForegroundService(Intent(context, InterventionForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, InterventionForegroundService::class.java))
        }

        /**
         * Creates the channel ahead of time, at app/service startup rather than on the first
         * intervention. This is a brand-new service class on every fresh install; its very
         * first-ever activation has to class-load the service and do this setup for the
         * first time, and that one-time cost landing in the same window as the overlay's
         * first frame is a plausible source of an intermittent stall on a cold start. Calling
         * this early removes that cost from the path a real intervention takes.
         */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Pause in progress", NotificationManager.IMPORTANCE_MIN)
                )
            }
        }
    }
}
