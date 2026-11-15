package com.flarehop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeightEstimatorTest {

    private fun gps(distanceM: Double, errorM: Double = 0.0) =
        GpsReference(distanceM = distanceM, bearingDeg = 0.0, errorM = errorM)

    private fun bluetooth(distanceM: Double, errorM: Double = 0.0) =
        BluetoothReference(rssi = -70, distanceM = distanceM, errorM = errorM)

    @Test
    fun heightIsTheMissingSideOfTheTriangle() {
        val estimate = HeightEstimator.estimate(gps(8.0), bluetooth(10.0))!!
        assertEquals(6.0, estimate.heightM, 0.001)
        assertEquals(2, estimate.floor)
    }

    @Test
    fun sameFlatAndStraightDistanceIsTheGroundFloor() {
        val estimate = HeightEstimator.estimate(gps(10.0), bluetooth(10.0))!!
        assertEquals(0.0, estimate.heightM, 0.001)
        assertEquals(0, estimate.floor)
    }

    @Test
    fun bluetoothShorterThanGpsMeansGroundNotAnError() {
        val estimate = HeightEstimator.estimate(gps(15.0), bluetooth(10.0))!!
        assertEquals(0.0, estimate.heightM, 0.001)
    }

    @Test
    fun straightAboveTheRescuerIsTheBluetoothDistance() {
        val estimate = HeightEstimator.estimate(gps(0.0), bluetooth(12.0))!!
        assertEquals(12.0, estimate.heightM, 0.001)
        assertEquals(4, estimate.floor)
    }

    @Test
    fun errorsWidenTheRangeAroundTheEstimate() {
        val estimate = HeightEstimator.estimate(gps(8.0, errorM = 2.0), bluetooth(10.0, 2.0))!!
        assertEquals(0.0, estimate.minM, 0.001)
        assertEquals(sqrt(144.0 - 36.0), estimate.maxM, 0.001)
        assertTrue(estimate.minM <= estimate.heightM && estimate.heightM <= estimate.maxM)
    }

    @Test
    fun heightNeedsBothGpsAndBluetooth() {
        assertNull(HeightEstimator.estimate(null, bluetooth(10.0)))
        assertNull(HeightEstimator.estimate(gps(10.0), null))
    }

    private fun sqrt(value: Double) = kotlin.math.sqrt(value)
}
