package com.flarehop.sense

import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SoundType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignsOfLifeTest {
    private val now = 1_000_000L
    private val engine = RealFlareEngine(clock = { now }, random = Random(1))
    private var battery: Int? = 80
    private val signs = SignsOfLife(engine, batteryPercent = { battery })

    private val loud = FloatArray(16_000) { 0.3f }
    private val quiet = FloatArray(16_000) { 0.001f }

    private fun carriedSound() = engine.mySos.value!!.record.sound

    @Before
    fun sendSos() {
        engine.sendSos(
            SosDetails(
                people = 1,
                injured = false,
                trapped = true,
                water = false,
                medical = false,
                fire = false,
            ),
            fix = null,
        )
    }

    @Test
    fun listensAllTheTimeFromThirtyPercent() {
        battery = 30
        assertTrue(signs.shouldListen(0))
        assertTrue(signs.shouldListen(5_000))
        assertTrue(signs.shouldListen(14_999))
    }

    @Test
    fun belowThirtyPercentListensForTheFirstFiveSecondsOfEveryFifteen() {
        battery = 29
        assertTrue(signs.shouldListen(0))
        assertTrue(signs.shouldListen(4_999))
        assertFalse(signs.shouldListen(5_000))
        assertFalse(signs.shouldListen(14_999))
        assertTrue(signs.shouldListen(15_000))
    }

    @Test
    fun demoSwitchListensAllTheTimeOnLowBattery() {
        battery = 20
        signs.listenContinuously = true
        assertTrue(signs.shouldListen(10_000))
    }

    @Test
    fun stopsBelowFifteenPercentEvenWhenContinuous() {
        signs.listenContinuously = true
        battery = 14
        assertFalse(signs.shouldListen(0))
        battery = 15
        assertTrue(signs.shouldListen(0))
    }

    @Test
    fun unknownBatteryKeepsListening() {
        battery = null
        assertTrue(signs.shouldListen(0))
        assertTrue(signs.shouldListen(10_000))
    }

    @Test
    fun confidentLoudKnockGoesIntoTheSos() {
        assertEquals(SoundType.KNOCK, signs.onWindow(listOf(Heard("Knock", 0.8f)), loud, now))
        assertEquals(SoundType.KNOCK, carriedSound())
    }

    @Test
    fun quietWindowSendsNothing() {
        assertNull(signs.onWindow(listOf(Heard("Knock", 0.8f)), quiet, now))
        assertEquals(SoundType.NONE, carriedSound())
    }

    @Test
    fun newestMatchWins() {
        signs.onWindow(listOf(Heard("Speech", 0.8f)), loud, now)
        signs.onWindow(listOf(Heard("Knock", 0.8f)), loud, now)
        assertEquals(SoundType.KNOCK, carriedSound())
    }

    @Test
    fun fakeKnockSetsKnock() {
        signs.fakeKnock(now)
        assertEquals(SoundType.KNOCK, carriedSound())
    }

    @Test
    fun heardSoundIsKeptWithItsExactTimeForTheVictimScreen() {
        signs.onWindow(listOf(Heard("Crying, sobbing", 0.7f)), loud, now + 4_321)
        assertEquals(HeardSound(SoundType.CRYING, now + 4_321), signs.lastHeard.value)
    }

    @Test
    fun crowdNoiseShowsWhileNoSignOfLifeWasHeard() {
        signs.onWindow(listOf(Heard("Crowd", 0.8f)), loud, now)
        assertEquals(SoundType.CROWD, carriedSound())
    }

    @Test
    fun crowdNoiseNeverReplacesAKnockOnTheSos() {
        signs.onWindow(listOf(Heard("Knock", 0.8f)), loud, now)
        signs.onWindow(listOf(Heard("Crowd", 0.9f)), loud, now + 1_000)
        assertEquals(SoundType.KNOCK, carriedSound())
        assertEquals(HeardSound(SoundType.CROWD, now + 1_000), signs.lastHeard.value)
    }

    @Test
    fun windowThatPassesNothingKeepsTheLastHeardSound() {
        signs.onWindow(listOf(Heard("Knock", 0.8f)), loud, now)
        signs.onWindow(listOf(Heard("Speech", 0.9f)), quiet, now + 1_000)
        assertEquals(HeardSound(SoundType.KNOCK, now), signs.lastHeard.value)
    }
}
