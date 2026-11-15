package com.flarehop.flare

import android.content.Context
import com.flarehop.sense.BatteryWatcher

object Flare {
    val engine: FlareEngine by lazy { RealFlareEngine() }

    private var batteryWatcher: BatteryWatcher? = null

    @Synchronized
    fun battery(context: Context): BatteryWatcher = batteryWatcher
        ?: BatteryWatcher.forDevice(context.applicationContext, engine).also { batteryWatcher = it }
}
