package com.flarehop.flare

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.flarehop.flare.FlareConstants.COMPANY_ID
import com.flarehop.flare.FlareConstants.PROTOCOL_VERSION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BleScanner(private val context: Context, private val engine: FlareEngine) {
    private var scanner: BluetoothLeScanner? = null
    private var job: Job? = null
    private var warnedOff = false
    private var failedAtMs = 0L

    private val filters = listOf(
        ScanFilter.Builder()
            .setManufacturerData(
                COMPANY_ID,
                byteArrayOf((PROTOCOL_VERSION shl 4).toByte()),
                byteArrayOf(0xF0.toByte()),
            )
            .build(),
    )

    private val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setReportDelay(0)
        .build()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bytes = result.scanRecord?.getManufacturerSpecificData(COMPANY_ID) ?: return
            engine.onPacket(bytes, result.rssi, System.currentTimeMillis())
        }

        override fun onScanFailed(errorCode: Int) {
            scanner = null
            failedAtMs = System.currentTimeMillis()
            Log.w(TAG, "Scan failed, error $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                keepScanning()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stop() {
        job?.cancel()
        job = null
        scanner?.stopScan(callback)
        scanner = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun keepScanning() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            if (!warnedOff) Log.w(TAG, "Bluetooth is off, not scanning")
            warnedOff = true
            scanner = null
            return
        }
        warnedOff = false
        if (scanner != null) return
        if (System.currentTimeMillis() - failedAtMs < RETRY_AFTER_FAILURE_MS) return
        val leScanner = adapter.bluetoothLeScanner ?: return
        leScanner.startScan(filters, settings, callback)
        scanner = leScanner
        Log.i(TAG, "Scanning started")
    }

    private companion object {
        const val TAG = "FlareScanner"
        const val CHECK_INTERVAL_MS = 2_000L
        const val RETRY_AFTER_FAILURE_MS = 30_000L
    }
}
