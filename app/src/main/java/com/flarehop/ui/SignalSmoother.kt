package com.flarehop.ui

import com.flarehop.flare.DirectSignal

// One Bluetooth reading swings by several dB from packet to packet, so cards and the radar show
// the median of the last few instead. The median ignores a single spike from someone walking by.
class SignalSmoother(private val window: Int = WINDOW) {
    private val readings = HashMap<Long, ArrayDeque<Int>>()
    private val carriers = HashMap<Long, Int>()

    fun add(signal: DirectSignal): Int {
        // After a handoff the signal comes from another phone in another place.
        if (carriers[signal.messageId] != signal.carrierId) readings.remove(signal.messageId)
        carriers[signal.messageId] = signal.carrierId
        val list = readings.getOrPut(signal.messageId) { ArrayDeque() }
        list.addLast(signal.rssi)
        while (list.size > window) list.removeFirst()
        return median(list)
    }

    fun clear() {
        readings.clear()
        carriers.clear()
    }

    private fun median(values: Collection<Int>): Int {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    companion object {
        const val WINDOW = 10
    }
}
