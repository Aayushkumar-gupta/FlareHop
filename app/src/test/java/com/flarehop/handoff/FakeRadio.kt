package com.flarehop.handoff

import com.flarehop.flare.FlareConstants.ADVERT_ROTATION_MS
import com.flarehop.flare.FlareEngine

class FakeRadio(startMs: Long = 1_000_000L) {
    var now: Long = startMs
        private set

    val clock: () -> Long = { now }

    private val phones = LinkedHashMap<String, FlareEngine>()
    private val off = mutableSetOf<String>()
    private val outOfRange = mutableSetOf<Set<String>>()
    private val rssi = mutableMapOf<Set<String>, Int>()

    fun <E : FlareEngine> add(name: String, engine: E): E {
        phones[name] = engine
        return engine
    }

    fun signal(a: String, b: String, dbm: Int) {
        rssi[setOf(a, b)] = dbm
    }

    fun cutLink(a: String, b: String) {
        outOfRange += setOf(a, b)
    }

    fun connect(a: String, b: String) {
        outOfRange -= setOf(a, b)
    }

    fun switchOff(name: String) {
        off += name
    }

    fun run(ms: Long) {
        val end = now + ms
        while (now < end) {
            now += ADVERT_ROTATION_MS
            val on = phones.filterKeys { it !in off }
            on.forEach { (sender, engine) ->
                val bytes = engine.nextPacket() ?: return@forEach
                on.forEach { (receiver, other) ->
                    // Android never hands a phone's own adverts to its own scanner.
                    val link = setOf(sender, receiver)
                    if (receiver != sender && link !in outOfRange) {
                        other.onPacket(bytes, rssi[link] ?: DEFAULT_RSSI, now)
                    }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_RSSI = -60
    }
}
