package com.flarehop.sense

import com.flarehop.flare.FlareConstants
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.SoundType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HeardSound(val type: SoundType, val atMs: Long)

class SignsOfLife(
    private val engine: FlareEngine,
    private val batteryPercent: () -> Int?,
    private val gate: SoundGate = SoundGate(),
) {
    @Volatile
    var listenContinuously = false

    // The SOS carries the sound's age in whole minutes, so the victim's own screen reads the exact
    // time from here instead.
    private val lastHeardState = MutableStateFlow<HeardSound?>(null)
    val lastHeard: StateFlow<HeardSound?> = lastHeardState.asStateFlow()

    // Listening 5 s in every 15 missed two thirds of all knocks and delayed the rest by up to 10 s,
    // so the microphone stays on until the battery reaches the radio's low power level.
    fun shouldListen(sinceStartMs: Long): Boolean {
        val battery = batteryPercent()
        if (battery != null && battery < STOP_BELOW_PERCENT) return false
        val lowPower = battery != null && battery < DUTY_CYCLE_BELOW_PERCENT
        return listenContinuously || !lowPower || sinceStartMs % PERIOD_MS < LISTEN_MS
    }

    fun onWindow(heard: List<Heard>, samples: FloatArray, atMs: Long): SoundType? {
        val type = gate.decide(heard, samples) ?: return null
        record(type, atMs)
        return type
    }

    fun fakeKnock(atMs: Long) {
        record(SoundType.KNOCK, atMs)
    }

    // Rescuers need the last knock or voice more than the noise around the phone, so crowd noise
    // only goes into the SOS while the SOS carries no sign of life.
    private fun record(type: SoundType, atMs: Long) {
        lastHeardState.value = HeardSound(type, atMs)
        val carried = engine.mySos.value?.record?.sound
        if (type == SoundType.CROWD && carried != null && carried.isSignOfLife) return
        engine.setSound(type, atMs)
    }

    companion object {
        const val LISTEN_MS = 5_000L
        const val PERIOD_MS = 15_000L
        const val STOP_BELOW_PERCENT = 15
        const val DUTY_CYCLE_BELOW_PERCENT = FlareConstants.LOW_POWER_BATTERY_PERCENT
    }
}
