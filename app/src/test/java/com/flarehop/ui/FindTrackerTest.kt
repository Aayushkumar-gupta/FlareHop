package com.flarehop.ui

import com.flarehop.flare.DirectSignal
import org.junit.Assert.assertEquals
import org.junit.Test

class FindTrackerTest {

    private fun signal(rssi: Int, carrierId: Int = 7, atMs: Long = 0L) =
        DirectSignal(messageId = 1L, carrierId = carrierId, rssi = rssi, atMs = atMs)

    private fun FindTracker.addAll(vararg rssi: Int): FindReading =
        rssi.map { add(signal(it)) }.last()

    @Test
    fun meterIsZeroAtMinus90AndWeaker() {
        assertEquals(0f, FindTracker.meter(-90.0), 0.0001f)
        assertEquals(0f, FindTracker.meter(-110.0), 0.0001f)
    }

    @Test
    fun meterIsFullAtMinus40AndStronger() {
        assertEquals(1f, FindTracker.meter(-40.0), 0.0001f)
        assertEquals(1f, FindTracker.meter(-20.0), 0.0001f)
    }

    @Test
    fun meterIsAStraightLineBetween() {
        assertEquals(0.5f, FindTracker.meter(-65.0), 0.0001f)
        assertEquals(0.2f, FindTracker.meter(-80.0), 0.0001f)
    }

    @Test
    fun beepsRunFrom1500msEmptyTo150msFull() {
        assertEquals(1_500L, FindTracker.beepIntervalMs(0f))
        assertEquals(150L, FindTracker.beepIntervalMs(1f))
        assertEquals(825L, FindTracker.beepIntervalMs(0.5f))
    }

    @Test
    fun smoothingAveragesFewerReadingsWhileWarmingUp() {
        val reading = FindTracker().addAll(-60, -70)
        assertEquals(-65.0, reading.smoothedRssi, 0.0001)
    }

    @Test
    fun smoothingKeepsOnlyTheLastTenReadings() {
        val tracker = FindTracker()
        tracker.addAll(-30, -30)
        val reading = tracker.addAll(-80, -80, -80, -80, -80, -80, -80, -80, -80, -80)
        assertEquals(-80.0, reading.smoothedRssi, 0.0001)
    }

    @Test
    fun newCarrierClearsTheReadings() {
        val tracker = FindTracker()
        tracker.addAll(-40, -40, -40)
        val reading = tracker.add(signal(rssi = -80, carrierId = 9))
        assertEquals(-80.0, reading.smoothedRssi, 0.0001)
        assertEquals(9, reading.carrierId)
    }

    @Test
    fun newCarrierRestartsTheTrend() {
        val tracker = FindTracker()
        tracker.addAll(-80, -80, -80, -80, -80, -70, -70, -70, -70)
        val reading = tracker.add(signal(rssi = -70, carrierId = 9))
        assertEquals(Trend.STEADY, reading.trend)
    }

    @Test
    fun trendIsSteadyUntilTenReadings() {
        val reading = FindTracker().addAll(-90, -90, -90, -90, -90, -50, -50, -50, -50)
        assertEquals(Trend.STEADY, reading.trend)
    }

    @Test
    fun trendIsWarmerWhenTheNewestFiveRiseBy3dB() {
        val reading = FindTracker().addAll(-80, -80, -80, -80, -80, -77, -77, -77, -77, -77)
        assertEquals(Trend.WARMER, reading.trend)
    }

    @Test
    fun trendIsColderWhenTheNewestFiveFallBy3dB() {
        val reading = FindTracker().addAll(-70, -70, -70, -70, -70, -73, -73, -73, -73, -73)
        assertEquals(Trend.COLDER, reading.trend)
    }

    @Test
    fun trendIsSteadyForLessThan3dB() {
        val reading = FindTracker().addAll(-70, -70, -70, -70, -70, -72, -72, -72, -72, -72)
        assertEquals(Trend.STEADY, reading.trend)
    }

    @Test
    fun readingKeepsTheSignalTime() {
        val reading = FindTracker().add(signal(rssi = -60, atMs = 12_345L))
        assertEquals(12_345L, reading.atMs)
    }
}
