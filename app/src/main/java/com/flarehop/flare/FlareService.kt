package com.flarehop.flare

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.flarehop.R
import com.flarehop.sense.BatteryWatcher
import com.flarehop.sense.YamnetListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FlareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var advertiser: BleAdvertiser
    private lateinit var scanner: BleScanner
    private lateinit var battery: BatteryWatcher
    private lateinit var listener: YamnetListener
    private var soundJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        advertiser = BleAdvertiser(this, Flare.engine)
        scanner = BleScanner(this, Flare.engine)
        battery = Flare.battery(this)
        listener = YamnetListener(this, Flare.signsOfLife(this), Flare::setListening)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        val types = serviceTypes(
            woken = intent?.getBooleanExtra(EXTRA_WOKEN, false) == true,
            location = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            microphone = granted(Manifest.permission.RECORD_AUDIO),
        )
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), types)
        battery.start(scope)
        advertiser.start(scope)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanner.start(scope)
            WakeScan.arm(this)
        }
        if (soundJob == null) {
            soundJob = scope.launch {
                var alerted: Long? = null
                Flare.engine.mySos.collect { mine ->
                    if (mine?.status == SosStatus.SENDING) startListening() else stopListening()
                    // Once per SOS another phone started, however often the record changes after.
                    if (mine?.startedBy != null && mine.record.messageId != alerted) {
                        alerted = mine.record.messageId
                        WakeNotice.showStartedRemotely(this@FlareService)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            advertiser.stop()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanner.stop()
        }
        listener.close()
        battery.stop()
        scope.cancel()
        running = false
        super.onDestroy()
    }

    private fun startListening() {
        if (granted(Manifest.permission.RECORD_AUDIO)) listener.start(scope)
    }

    private fun stopListening() {
        listener.stop()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.beacon_channel_name))
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.beacon_notification_title))
            .setContentText(getString(R.string.beacon_notification_text))
            .setOngoing(true)
            .setContentIntent(openApp(this))
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "beacon"
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_WOKEN = "woken"

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FlareService::class.java))
        }

        // Started by WakeReceiver with the app closed. If Android refuses a foreground service
        // from the background, the notification asks for one tap instead.
        fun wake(context: Context) {
            val intent = Intent(context, FlareService::class.java).putExtra(EXTRA_WOKEN, true)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: ForegroundServiceStartNotAllowedException) {
                WakeNotice.show(context)
            }
        }

        // Android lets a service started from the background use location and the microphone
        // only once the app is open, so a woken phone starts as a Bluetooth carrier only. Opening
        // the app calls start() again, which adds the other types.
        internal fun serviceTypes(woken: Boolean, location: Boolean, microphone: Boolean): Int {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (woken) return types
            if (location) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (microphone) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            return types
        }

        // Tapping the notification opens the app, where the owner can add their own SOS.
        fun openApp(context: Context): PendingIntent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return null
            return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FlareService::class.java))
        }
    }
}
