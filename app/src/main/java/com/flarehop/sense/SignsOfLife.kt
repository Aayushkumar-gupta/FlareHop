package com.flarehop.sense

import com.flarehop.flare.FlareEngine
import com.flarehop.flare.SoundType

class SignsOfLife(
    private val engine: FlareEngine,
    private val batteryPercent: () -> Int?,
    private val gate: SoundGate = SoundGate(),
) {
    @Volatile
    var listenContinuously = false

    fun shouldListen(sinceStartMs: Long): Boolean {
        val battery = batteryPercent()
        if (battery != null && battery < STOP_BELOW_PERCENT) return false
        return listenContinuously || sinceStartMs % PERIOD_MS < LISTEN_MS
    }

    fun onWindow(heard: List<Heard>, samples: FloatArray, atMs: Long): SoundType? {
        val type = gate.decide(heard, samples) ?: return null
        engine.setSound(type, atMs)
        return type
    }

    fun fakeKnock(atMs: Long) {
        engine.setSound(SoundType.KNOCK, atMs)
    }

    companion object {
        const val LISTEN_MS = 5_000L
        const val PERIOD_MS = 15_000L
        const val STOP_BELOW_PERCENT = 15
    }
}
