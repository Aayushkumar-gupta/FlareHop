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
}
