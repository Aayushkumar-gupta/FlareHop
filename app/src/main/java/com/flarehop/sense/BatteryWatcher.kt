package com.flarehop.sense

import android.content.Context
import android.os.BatteryManager
import com.flarehop.flare.FlareEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryWatcher(
    private val engine: FlareEngine,
    private val readPercent: () -> Int?,
    private val intervalMs: Long = INTERVAL_MS,
) {
    @Volatile
    var overridePercent: Int? = null
        set(value) {
            field = value
            push()
        }

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                push()
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun push() {
        val percent = overridePercent ?: readPercent() ?: return
        engine.setBattery(percent)
    }

    companion object {
        const val INTERVAL_MS = 60_000L

        fun forDevice(context: Context, engine: FlareEngine): BatteryWatcher {
            val manager = context.getSystemService(BatteryManager::class.java)
            return BatteryWatcher(engine, readPercent = { capacity(manager) })
        }

        // Some devices return Int.MIN_VALUE instead of a level when the property is unsupported.
        private fun capacity(manager: BatteryManager?): Int? = manager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }
}
