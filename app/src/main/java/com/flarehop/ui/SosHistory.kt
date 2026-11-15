package com.flarehop.ui

import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType

data class CarrierSeen(
    val phoneId: Int,
    val isVictim: Boolean,
    val battery: Int?,
    val rssi: Int?,
    val lastHeardAt: Long,
)

enum class HistoryKind { FIRST_HEARD, CARRIER, SOUND, POSITION, BATTERY, CANCELLED }

data class HistoryEntry(val atMs: Long, val kind: HistoryKind, val text: String)

data class SosTrack(val carriers: List<CarrierSeen>, val history: List<HistoryEntry>)

// What a rescuer phone can know about one SOS: every phone it heard broadcasting it, and each
// change in the packets. The engine keeps only the latest packet, so this keeps the past.
// Kept free of Android so it can be checked without a phone.
class SosHistory {
    private val last = HashMap<Long, SosRecord>()
    private val carriers = HashMap<Long, LinkedHashMap<Int, CarrierSeen>>()
    private val history = HashMap<Long, ArrayDeque<HistoryEntry>>()

    fun update(records: List<SosRecord>): Map<Long, SosTrack> {
        records.forEach { record ->
            val before = last[record.messageId]
            if (before == null || record.lastHeardAt != before.lastHeardAt) heardFrom(record)
            changes(before, record).forEach { add(record.messageId, it) }
            last[record.messageId] = record
        }
        return last.keys.associateWith { id ->
            SosTrack(
                carriers = carriers[id]?.values?.toList().orEmpty(),
                history = history[id]?.toList().orEmpty(),
            )
        }
    }

    private fun heardFrom(record: SosRecord) {
        val seen = carriers.getOrPut(record.messageId) { LinkedHashMap() }
        seen[record.carrierId] = CarrierSeen(
            phoneId = record.carrierId,
            isVictim = record.carrierId == record.originId,
            battery = record.battery,
            rssi = record.lastRssi,
            lastHeardAt = record.lastHeardAt,
        )
    }

    private fun add(messageId: Long, entry: HistoryEntry) {
        val entries = history.getOrPut(messageId) { ArrayDeque() }
        entries.addLast(entry)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    private fun changes(before: SosRecord?, now: SosRecord): List<HistoryEntry> {
        val at = now.lastHeardAt
        fun entry(kind: HistoryKind, text: String) = HistoryEntry(at, kind, text)
        if (before == null) {
            return listOfNotNull(
                entry(HistoryKind.FIRST_HEARD, "First heard from ${carrierText(now)}"),
                now.takeIf { it.sound != SoundType.NONE }
                    ?.let { entry(HistoryKind.SOUND, "${soundName(it.sound)} heard") },
                positionText(null, now)?.let { entry(HistoryKind.POSITION, it) },
            )
        }
        return listOfNotNull(
            now.takeIf { it.carrierId != before.carrierId }?.let {
                val text = if (it.carrierId == it.originId) {
                    "The victim's phone ${phoneLabel(it.originId)} is sending again"
                } else {
                    "Now carried by ${carrierText(it)}"
                }
                entry(HistoryKind.CARRIER, text)
            },
            now.takeIf { soundIsNew(before, it) }
                ?.let { entry(HistoryKind.SOUND, "${soundName(it.sound)} heard") },
            positionText(before, now)?.let { entry(HistoryKind.POSITION, it) },
            now.takeIf { batteryStepped(before, it) }
                ?.let { entry(HistoryKind.BATTERY, "Battery ${it.battery}%") },
            now.takeIf { it.cancelled && !before.cancelled }
                ?.let { entry(HistoryKind.CANCELLED, "Cancelled by the victim") },
        )
    }

    // A sound counts again when its type changes, or when its age drops, which means the victim's
    // phone heard it once more. The age is in seconds, so a repeat only counts after a quiet
    // minute; steady knocking would otherwise add an entry every second.
    private fun soundIsNew(before: SosRecord, now: SosRecord): Boolean {
        if (now.sound == SoundType.NONE) return false
        if (now.sound != before.sound) return true
        val was = before.soundAgeSec ?: return false
        val age = now.soundAgeSec ?: return false
        return age < was && was >= REPEAT_AFTER_SEC
    }

    // Battery is logged in 10% steps from the same carrier; a new carrier's battery is part of
    // its own entry.
    private fun batteryStepped(before: SosRecord, now: SosRecord): Boolean {
        if (now.carrierId != before.carrierId) return false
        val was = before.battery ?: return false
        val level = now.battery ?: return false
        return level / BATTERY_STEP != was / BATTERY_STEP
    }

    private fun positionText(before: SosRecord?, now: SosRecord): String? {
        val lat = now.latitude ?: return null
        val lon = now.longitude ?: return null
        val accuracy = now.accuracyM?.let { " (±$it m)" } ?: ""
        val wasLat = before?.latitude
        val wasLon = before?.longitude
        if (wasLat == null || wasLon == null) return "GPS position received$accuracy"
        val moved = distanceMeters(wasLat, wasLon, lat, lon)
        val threshold = maxOf(MIN_MOVE_M, (now.accuracyM ?: 0).toDouble())
        if (moved < threshold) return null
        return "Position moved ${distanceText(moved)}$accuracy"
    }

    private fun carrierText(record: SosRecord): String {
        val battery = record.battery?.let { " ($it%)" } ?: ""
        val who = if (record.carrierId == record.originId) "the victim's phone" else "phone"
        return "$who ${phoneLabel(record.carrierId)}$battery"
    }

    private fun soundName(sound: SoundType) = when (sound) {
        SoundType.NONE -> "No sound"
        SoundType.SPEECH -> "Speech"
        SoundType.SHOUT -> "Shouting"
        SoundType.KNOCK -> "Knocking"
        SoundType.CRYING -> "Crying"
        SoundType.CROWD -> "Crowd noise"
    }

    companion object {
        const val MAX_ENTRIES = 100
        const val BATTERY_STEP = 10
        const val MIN_MOVE_M = 20.0
        const val REPEAT_AFTER_SEC = 60
    }
}
