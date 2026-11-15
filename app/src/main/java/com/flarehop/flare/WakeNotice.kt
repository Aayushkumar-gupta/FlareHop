package com.flarehop.flare

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flarehop.R

// Shown when Android refuses to start the service from the background: one tap opens the app,
// which starts it.
object WakeNotice {
    private const val CHANNEL_ID = "sos_nearby"
    private const val NOTIFICATION_ID = 2

    fun show(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.wake_channel_name))
                .build(),
        )
        val notice = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(context.getString(R.string.wake_notification_title))
            .setContentText(context.getString(R.string.wake_notification_text))
            .setContentIntent(FlareService.openApp(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(NOTIFICATION_ID, notice)
    }

    // Another phone started this phone's SOS: its owner may not be looking, so it rings and
    // buzzes, and one tap opens the app where they can cancel it if they are safe.
    fun showStartedRemotely(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(STARTED_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.started_channel_name))
                .setVibrationEnabled(true)
                .setVibrationPattern(BUZZ)
                .build(),
        )
        val notice = NotificationCompat.Builder(context, STARTED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(context.getString(R.string.started_notification_title))
            .setContentText(context.getString(R.string.started_notification_text))
            .setContentIntent(FlareService.openApp(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(STARTED_NOTIFICATION_ID, notice)
    }

    private const val STARTED_CHANNEL_ID = "sos_started"
    private const val STARTED_NOTIFICATION_ID = 3
    private val BUZZ = longArrayOf(0L, 600L, 300L, 600L, 300L, 600L)
}
