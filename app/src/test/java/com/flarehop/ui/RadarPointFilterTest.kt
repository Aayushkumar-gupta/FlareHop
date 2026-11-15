package com.flarehop.ui

import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarPointFilterTest {

    private val here = GpsFix(27.7, 85.3, accuracyM = 3.0, ageMin = 0)

    // About 0.000009 degrees of latitude per metre.
    private fun northBy(meters: Double) = record(latitude = 27.7 + meters / 111_195.0)

    private fun record(latitude: Double?) = SosRecord(
        messageId = 1L,
        originId = 10,
        carrierId = 10,
        latitude = latitude,
        longitude = latitude?.let { 85.3 },
        accuracyM = 3,
        fixAgeMin = 0,
        people = 0,
        injured = false,
        trapped = false,
        water = false,
        medical = false,
        fire = false,
        battery = 80,
        sound = SoundType.NONE,
        soundAgeSec = null,
        firstHeardAt = 0L,
        lastHeardAt = 0L,
        lastRssi = -70,
        acked = false,
        cancelled = false,
    )

    private fun shownDistance(filter: RadarPointFilter, meters: Double) =
        filter.update(here, listOf(northBy(meters)))[1L]!!.distanceM

    @Test
    fun firstReadingIsShown() {
        assertEquals(10.0, shownDistance(RadarPointFilter(), 10.0), 0.1)
    }

    @Test
    fun tinyMoveIsIgnored() {
        val filter = RadarPointFilter()
        shownDistance(filter, 10.0)
        assertEquals(10.0, shownDistance(filter, 10.5), 0.1)
    }

    @Test
    fun normalMoveIsFollowed() {
        val filter = RadarPointFilter()
        shownDistance(filter, 10.0)
        assertEquals(15.0, shownDistance(filter, 15.0), 0.1)
    }

    @Test
    fun leapIsIgnored() {
        val filter = RadarPointFilter()
        shownDistance(filter, 10.0)
        assertEquals(10.0, shownDistance(filter, 60.0), 0.1)
    }

    @Test
    fun leapThatHoldsIsTaken() {
        val filter = RadarPointFilter()
        shownDistance(filter, 10.0)
        assertEquals(10.0, shownDistance(filter, 60.0), 0.1)
        assertEquals(10.0, shownDistance(filter, 61.0), 0.1)
        assertEquals(60.0, shownDistance(filter, 60.0), 0.1)
    }

    @Test
    fun leapsToDifferentPlacesAreAllIgnored() {
        val filter = RadarPointFilter()
        shownDistance(filter, 10.0)
        assertEquals(10.0, shownDistance(filter, 60.0), 0.1)
        assertEquals(10.0, shownDistance(filter, 110.0), 0.1)
        assertEquals(10.0, shownDistance(filter, 60.0), 0.1)
    }

    @Test
    fun lostFixKeepsTheLastPoint() {
        val filter = RadarPointFilter()
        shownDistance(filter, 10.0)
        val shown = filter.update(null, listOf(northBy(10.0)))
        assertEquals(10.0, shown[1L]!!.distanceM, 0.1)
    }

    @Test
    fun gapBetweenTwoPointsAtRightAngles() {
        val a = GpsReference(distanceM = 3.0, bearingDeg = 0.0, errorM = 1.0)
        val b = GpsReference(distanceM = 4.0, bearingDeg = 90.0, errorM = 1.0)
        assertEquals(5.0, RadarPointFilter.gap(a, b), 0.001)
    }

    @Test
    fun recordWithoutFixHasNoPoint() {
        assertTrue(RadarPointFilter().update(here, listOf(record(latitude = null))).isEmpty())
    }
}
