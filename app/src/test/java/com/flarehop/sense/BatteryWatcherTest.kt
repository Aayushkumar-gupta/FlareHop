package com.flarehop.sense

import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.SosDetails
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryWatcherTest {
    private val engine = RealFlareEngine(clock = { 1_000_000L }, random = Random(1))
    private var level: Int? = 80
    private val watcher = BatteryWatcher(engine, readPercent = { level })

    private val details = SosDetails(
        people = 1,
        injured = false,
        trapped = false,
        water = false,
        medical = false,
        fire = false,
    )

    private fun carriedBattery() = engine.mySos.value!!.record.battery

    @Before
    fun sendSos() {
        engine.sendSos(details, fix = null)
    }

    @Test
    fun pushesTheLevelAsSoonAsItStarts() = runTest {
        watcher.start(backgroundScope)
        runCurrent()
        assertEquals(80, carriedBattery())
    }

    @Test
    fun pushesANewLevelEveryMinute() = runTest {
        watcher.start(backgroundScope)
        runCurrent()
        level = 42
        advanceTimeBy(BatteryWatcher.INTERVAL_MS - 1)
        runCurrent()
        assertEquals(80, carriedBattery())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(42, carriedBattery())
    }

    @Test
    fun overrideWinsOverTheRealLevel() = runTest {
        watcher.start(backgroundScope)
        runCurrent()
        watcher.overridePercent = 9
        assertEquals(9, carriedBattery())
        advanceTimeBy(BatteryWatcher.INTERVAL_MS)
        runCurrent()
        assertEquals(9, carriedBattery())
    }

    @Test
    fun clearingTheOverrideReturnsToTheRealLevel() {
        watcher.overridePercent = 9
        watcher.overridePercent = null
        assertEquals(80, carriedBattery())
    }

    @Test
    fun unreadableLevelLeavesTheLastOneInPlace() = runTest {
        engine.setBattery(55)
        level = null
        watcher.start(backgroundScope)
        runCurrent()
        assertEquals(55, carriedBattery())
    }

    @Test
    fun stopEndsTheUpdates() = runTest {
        watcher.start(backgroundScope)
        runCurrent()
        watcher.stop()
        level = 30
        advanceTimeBy(2 * BatteryWatcher.INTERVAL_MS)
        runCurrent()
        assertEquals(80, carriedBattery())
    }
}
