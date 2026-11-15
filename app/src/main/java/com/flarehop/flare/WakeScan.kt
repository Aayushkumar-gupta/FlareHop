package com.flarehop.flare

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresPermission

// A scan Android keeps running for the app after its process dies, delivered to WakeReceiver.
// It stays armed while the service runs too, so a process killed without warning is still woken.
object WakeScan {
    private const val TAG = "FlareWake"
    private const val REQUEST_CODE = 1

    private val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .build()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun arm(context: Context) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return
        val result = scanner.startScan(BleScanner.FILTERS, settings, wakeIntent(context))
        if (result != 0 && result != ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
            Log.w(TAG, "Wake scan not armed, error $result")
        }
    }

    // The scan result extras are filled in by the system, so the intent has to be mutable.
    private fun wakeIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WakeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
