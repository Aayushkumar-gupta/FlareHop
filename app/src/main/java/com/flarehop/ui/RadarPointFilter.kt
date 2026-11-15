package com.flarehop.ui

import com.flarehop.flare.SosRecord
import kotlin.math.cos
import kotlin.math.sqrt

// GPS wanders a little between readings and now and then leaps tens of metres. The radar keeps a
// survivor's square still for tiny moves, skips leaps, and follows every move in between. A leap
// that holds for JUMP_CONFIRM readings is real, such as GPS locking on after a rough network fix,
// so it is taken instead of leaving the square stuck at the old spot.
class RadarPointFilter(
    private val minMoveM: Double = MIN_MOVE_M,
    private val maxJumpM: Double = MAX_JUMP_M,
    private val jumpConfirm: Int = JUMP_CONFIRM,
) {
    private class Jump(val point: GpsReference, val count: Int)

    private var shown = emptyMap<Long, GpsReference>()
    private val jumps = mutableMapOf<Long, Jump>()

    fun update(here: GpsFix?, records: List<SosRecord>): Map<Long, GpsReference> {
        shown = records.mapNotNull { record ->
            val old = shown[record.messageId]
            val fresh = here?.let { h -> fixOf(record)?.let { LocationEstimator.gps(h, it) } }
            val point = when {
                fresh == null -> old
                old == null -> fresh
                gap(old, fresh) > maxJumpM -> if (jumpHeld(record.messageId, fresh)) fresh else old
                gap(old, fresh) >= minMoveM -> fresh
                else -> old
            }
            // Any reading that is not an ignored leap ends the count.
            if (point !== old || old == null || fresh == null || gap(old, fresh) <= maxJumpM) {
                jumps.remove(record.messageId)
            }
            point?.let { record.messageId to it }
        }.toMap()
        jumps.keys.retainAll(shown.keys)
        return shown
    }

    // Counts readings that land near the previous leap; one in a new place starts the count again.
    private fun jumpHeld(messageId: Long, fresh: GpsReference): Boolean {
        val last = jumps[messageId]
        val count = if (last != null && gap(last.point, fresh) <= maxJumpM) last.count + 1 else 1
        jumps[messageId] = Jump(fresh, count)
        return count >= jumpConfirm
    }

    companion object {
        const val MIN_MOVE_M = 1.0
        const val MAX_JUMP_M = 15.0
        const val JUMP_CONFIRM = 3

        // Both points are measured from this phone, so the law of cosines gives the gap.
        fun gap(a: GpsReference, b: GpsReference): Double {
            val turn = Math.toRadians(a.bearingDeg - b.bearingDeg)
            val squared = a.distanceM * a.distanceM + b.distanceM * b.distanceM -
                2 * a.distanceM * b.distanceM * cos(turn)
            return sqrt(squared.coerceAtLeast(0.0))
        }
    }
}
