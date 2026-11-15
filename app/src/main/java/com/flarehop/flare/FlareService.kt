package com.flarehop.flare

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
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
        advertiser = BleAdvertiser(this, Flare.engine)
        scanner = BleScanner(this, Flare.engine)
        battery = Flare.battery(this)
        listener = YamnetListener(this, Flare.signsOfLife(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), types)
        battery.start(scope)
        advertiser.start(scope)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanner.start(scope)
        }
        if (soundJob == null) {
            soundJob = scope.launch {
                Flare.engine.mySos.collect { mine ->
                    if (mine?.status == SosStatus.SENDING) startListening() else stopListening()
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
        stopListening()
        battery.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        if (Flare.listening.value || !granted(Manifest.permission.RECORD_AUDIO)) return
        runCatching { listener.start(scope) }
            .onSuccess { Flare.setListening(true) }
            .onFailure { Log.w(TAG, "Sound listener did not start", it) }
    }

    private fun stopListening() {
        if (!Flare.listening.value) return
        listener.stop()
        Flare.setListening(false)
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
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "beacon"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "FlareService"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FlareService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FlareService::class.java))
        }
    }
}
