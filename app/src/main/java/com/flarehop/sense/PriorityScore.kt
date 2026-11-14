package com.flarehop.sense

import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType

enum class PriorityBand { CRITICAL, HIGH, NORMAL }

object PriorityScore {

    fun score(record: SosRecord, nowMs: Long): Int {
        var points = 0
        if (record.trapped) points += 40
        if (record.injured) points += 30
        if (record.medical) points += 20
        if (record.water) points += 20
        if (record.fire) points += 20
        points += record.people.coerceAtMost(10) * 5
        if (heardSoundRecently(record, nowMs)) points += 20
        val battery = record.battery
        if (battery != null && battery < 20) points += 15
        points += minutesBetween(record.firstHeardAt, nowMs).coerceAtMost(60) / 5
        return points
    }

    fun band(score: Int): PriorityBand = when {
        score >= 70 -> PriorityBand.CRITICAL
        score >= 40 -> PriorityBand.HIGH
        else -> PriorityBand.NORMAL
    }

    fun mostUrgentFirst(nowMs: Long): Comparator<SosRecord> =
        compareByDescending<SosRecord> { score(it, nowMs) }.thenBy { it.firstHeardAt }

    private fun heardSoundRecently(record: SosRecord, nowMs: Long): Boolean {
        if (record.sound == SoundType.NONE) return false
        val ageAtLastCopy = record.soundAgeMin ?: return false
        // soundAgeMin was measured when the newest copy arrived, so the time since then must be added.
        return ageAtLastCopy + minutesBetween(record.lastHeardAt, nowMs) <= 10
    }

    private fun minutesBetween(fromMs: Long, toMs: Long): Int =
        ((toMs - fromMs) / 60_000L).toInt().coerceAtLeast(0)
}
