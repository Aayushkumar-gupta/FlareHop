package com.flarehop.flare

import android.content.Context
import com.flarehop.sense.BatteryWatcher
import com.flarehop.sense.SignsOfLife
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Flare {
    val engine: FlareEngine by lazy { RealFlareEngine() }

    private var batteryWatcher: BatteryWatcher? = null
    private var signs: SignsOfLife? = null
    private val listeningState = MutableStateFlow(false)

    val listening: StateFlow<Boolean> = listeningState.asStateFlow()

    @Synchronized
    fun battery(context: Context): BatteryWatcher = batteryWatcher
        ?: BatteryWatcher.forDevice(context.applicationContext, engine).also { batteryWatcher = it }

    @Synchronized
    fun signsOfLife(context: Context): SignsOfLife = signs
        ?: SignsOfLife(engine, battery(context)::currentPercent).also { signs = it }

    internal fun setListening(on: Boolean) {
        listeningState.value = on
    }
}
