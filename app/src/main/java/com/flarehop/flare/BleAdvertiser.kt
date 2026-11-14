package com.flarehop.flare

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.flarehop.flare.FlareConstants.ADVERT_ROTATION_MS
import com.flarehop.flare.FlareConstants.COMPANY_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BleAdvertiser(private val context: Context, private val engine: FlareEngine) {
    private var state = AdvertState.STOPPED
    private var last: ByteArray? = null
    private var set: AdvertisingSet? = null
    private var job: Job? = null

    private val parameters = AdvertisingSetParameters.Builder()
        .setLegacyMode(true)
        .setConnectable(false)
        .setScannable(false)
        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
        .build()

    private val callback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(
            advertisingSet: AdvertisingSet?,
            txPower: Int,
            status: Int,
        ) {
            if (status == ADVERTISE_SUCCESS && advertisingSet != null) {
                set = advertisingSet
                state = AdvertState.ON
                Log.i(TAG, "Advertising started, tx power $txPower dBm")
            } else {
                clear()
                Log.w(TAG, "Advertising did not start, status $status")
            }
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            clear()
            Log.i(TAG, "Advertising stopped")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                step()
                delay(ADVERT_ROTATION_MS)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stop() {
        job?.cancel()
        job = null
        leAdvertiser()?.stopAdvertisingSet(callback)
        clear()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun step() {
        val advertiser = leAdvertiser()
        if (advertiser == null) {
            if (state != AdvertState.STOPPED) Log.w(TAG, "Bluetooth is off, advertising paused")
            clear()
            return
        }
        when (val action = AdvertPlanner.decide(state, last, engine.nextPacket())) {
            is AdvertAction.Start -> {
                state = AdvertState.STARTING
                last = action.bytes
                advertiser.startAdvertisingSet(
                    parameters,
                    data(action.bytes),
                    null,
                    null,
                    null,
                    callback,
                )
            }

            is AdvertAction.Update -> {
                set?.setAdvertisingData(data(action.bytes))
                last = action.bytes
            }

            is AdvertAction.Resume -> {
                set?.setAdvertisingData(data(action.bytes))
                set?.enableAdvertising(true, 0, 0)
                state = AdvertState.ON
                last = action.bytes
            }

            AdvertAction.Pause -> {
                set?.enableAdvertising(false, 0, 0)
                state = AdvertState.PAUSED
                last = null
            }

            AdvertAction.Wait -> Unit
        }
    }

    private fun leAdvertiser(): BluetoothLeAdvertiser? =
        context.getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.takeIf { it.isEnabled }
            ?.bluetoothLeAdvertiser

    private fun data(bytes: ByteArray): AdvertiseData = AdvertiseData.Builder()
        .addManufacturerData(COMPANY_ID, bytes)
        .setIncludeDeviceName(false)
        .setIncludeTxPowerLevel(false)
        .build()

    private fun clear() {
        set = null
        state = AdvertState.STOPPED
        last = null
    }

    private companion object {
        const val TAG = "FlareAdvertiser"
    }
}
