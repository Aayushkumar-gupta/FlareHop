package com.flarehop.ui

import kotlin.math.sqrt

data class HeightEstimate(val heightM: Double, val minM: Double, val maxM: Double) {
    val floor: Int get() = HeightEstimator.floorOf(heightM)
    val minFloor: Int get() = HeightEstimator.floorOf(minM)
    val maxFloor: Int get() = HeightEstimator.floorOf(maxM)
}

// The packet carries no altitude (Contract 1), so height comes from geometry instead. With the
// rescuer on the ground, GPS gives the flat distance to the victim and Bluetooth the straight-line
// one; the gap between the two is how far up the victim is.
object HeightEstimator {
    const val FLOOR_HEIGHT_M = 3.0

    fun estimate(gps: GpsReference?, bluetooth: BluetoothReference?): HeightEstimate? {
        if (gps == null || bluetooth == null) return null
        val slant = bluetooth.distanceM
        val flat = gps.distanceM
        return HeightEstimate(
            heightM = height(slant, flat),
            // The lowest height fits the shortest slant with the longest flat distance, and the
            // highest the other way round.
            minM = height(slant - bluetooth.errorM, flat + gps.errorM),
            maxM = height(slant + bluetooth.errorM, flat - gps.errorM),
        )
    }

    // 0 is the ground floor, the floor the rescuer is standing on.
    fun floorOf(heightM: Double): Int = (heightM / FLOOR_HEIGHT_M).toInt()

    private fun height(slantM: Double, flatM: Double): Double {
        val slant = slantM.coerceAtLeast(0.0)
        val flat = flatM.coerceAtLeast(0.0)
        return if (slant <= flat) 0.0 else sqrt(slant * slant - flat * flat)
    }
}
