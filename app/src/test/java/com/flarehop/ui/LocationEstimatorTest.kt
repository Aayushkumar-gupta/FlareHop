package com.flarehop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationEstimatorTest {

    private fun gpsAt(distanceM: Double, errorM: Double, bearingDeg: Double = 90.0) =
        GpsReference(distanceM = distanceM, bearingDeg = bearingDeg, errorM = errorM)

    private fun bluetoothAt(distanceM: Double, errorM: Double) =
        BluetoothReference(rssi = -70, distanceM = distanceM, errorM = errorM)

    @Test
    fun signalAtTheOneMetreLevelIsOneMetre() {
        assertEquals(1.0, LocationEstimator.bluetooth(-59).distanceM, 0.001)
    }

    @Test
    fun twentyDbWeakerIsTenTimesFarther() {
        assertEquals(10.0, LocationEstimator.bluetooth(-79).distanceM, 0.001)
    }

    @Test
    fun bluetoothDistanceStaysInsideItsRange() {
        assertEquals(0.5, LocationEstimator.bluetooth(-20).distanceM, 0.001)
        assertEquals(150.0, LocationEstimator.bluetooth(-130).distanceM, 0.001)
    }

    @Test
    fun bluetoothErrorGrowsWithDistance() {
        assertEquals(6.0, LocationEstimator.bluetooth(-79).errorM, 0.001)
    }

    @Test
    fun gpsErrorCombinesBothPhonesAccuracy() {
        val here = GpsFix(27.7000, 85.3000, accuracyM = 3.0, ageMin = 0)
        val there = GpsFix(27.7001, 85.3000, accuracyM = 4.0, ageMin = 0)
        val gps = assertNotNullAndGet(LocationEstimator.gps(here, there))
        assertEquals(5.0, gps.errorM, 0.001)
        assertEquals(11.1, gps.distanceM, 0.1)
        assertEquals(0.0, gps.bearingDeg, 0.1)
    }

    @Test
    fun oldOrRoughFixesAreNotUsed() {
        val here = GpsFix(27.7000, 85.3000, accuracyM = 10.0, ageMin = 0)
        val there = GpsFix(27.7001, 85.3000, accuracyM = 10.0, ageMin = 70)
        assertNull(LocationEstimator.gps(here, there))
    }

    @Test
    fun bothSourcesGiveASmallerErrorThanEither() {
        val estimate = assertNotNullAndGet(
            LocationEstimator.estimate(gpsAt(20.0, 5.0), bluetoothAt(10.0, 6.0)),
        )
        assertEquals(EstimateSource.GPS_AND_BLUETOOTH, estimate.source)
        assertEquals(15.90, estimate.distanceM, 0.01)
        assertEquals(3.84, estimate.errorM, 0.01)
        assertEquals(90.0, estimate.bearingDeg!!, 0.001)
    }

    @Test
    fun theMoreAccurateSourceCountsForMore() {
        val estimate = assertNotNullAndGet(
            LocationEstimator.estimate(gpsAt(40.0, 30.0), bluetoothAt(10.0, 3.0)),
        )
        assertTrue(estimate.distanceM < 11.0)
    }

    @Test
    fun gpsFarOutsideBluetoothRangeIsIgnored() {
        val estimate = assertNotNullAndGet(
            LocationEstimator.estimate(gpsAt(400.0, 5.0), bluetoothAt(10.0, 6.0)),
        )
        assertEquals(EstimateSource.BLUETOOTH, estimate.source)
        assertEquals(10.0, estimate.distanceM, 0.001)
        assertNull(estimate.bearingDeg)
        assertNotNull(estimate.gps)
    }

    @Test
    fun directionIsUnknownWhenThePhonesAreInsideTheGpsError() {
        val estimate = assertNotNullAndGet(LocationEstimator.estimate(gpsAt(8.0, 15.0), null))
        assertEquals(EstimateSource.GPS, estimate.source)
        assertNull(estimate.bearingDeg)
    }

    @Test
    fun bluetoothAloneHasNoDirection() {
        val estimate = assertNotNullAndGet(LocationEstimator.estimate(null, bluetoothAt(10.0, 6.0)))
        assertEquals(EstimateSource.BLUETOOTH, estimate.source)
        assertNull(estimate.bearingDeg)
    }

    @Test
    fun nothingToGoOnGivesNoEstimate() {
        assertNull(LocationEstimator.estimate(null, null))
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
