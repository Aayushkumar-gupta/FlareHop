package com.flarehop.sense

import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundGateTest {
    private val gate = SoundGate(loudnessFloorDbfs = -40f)

    private fun window(level: Float, size: Int = 16_000) = FloatArray(size) { level }

    @Test
    fun everyContractClassMapsToItsSoundType() {
        val expected = mapOf(
            "Speech" to SoundType.SPEECH,
            "Shout" to SoundType.SHOUT,
            "Yell" to SoundType.SHOUT,
            "Screaming" to SoundType.SHOUT,
            "Children shouting" to SoundType.SHOUT,
            "Knock" to SoundType.KNOCK,
            "Tap" to SoundType.KNOCK,
            "Crying, sobbing" to SoundType.CRYING,
            "Baby cry, infant cry" to SoundType.CRYING,
            "Whimper" to SoundType.CRYING,
        )
        expected.forEach { (label, type) -> assertEquals(label, type, SoundGate.typeFor(label)) }
    }

    @Test
    fun otherLabelsMapToNothing() {
        assertNull(SoundGate.typeFor("Music"))
        assertNull(SoundGate.typeFor("Typing"))
    }

    @Test
    fun confidentLoudKnockIsKnock() {
        assertEquals(SoundType.KNOCK, gate.decide(listOf(Heard("Knock", 0.7f)), window(0.3f)))
    }

    @Test
    fun scoreBelowHalfIsIgnored() {
        assertNull(gate.decide(listOf(Heard("Knock", 0.49f)), window(0.3f)))
    }

    @Test
    fun scoreOfExactlyHalfCounts() {
        assertEquals(SoundType.SPEECH, gate.decide(listOf(Heard("Speech", 0.5f)), window(0.3f)))
    }

    @Test
    fun quietWindowIsIgnoredEvenWhenConfident() {
        assertNull(gate.decide(listOf(Heard("Speech", 0.9f)), window(0.001f)))
    }

    @Test
    fun strongestMappedClassWins() {
        val heard = listOf(Heard("Speech", 0.6f), Heard("Knock", 0.8f))
        assertEquals(SoundType.KNOCK, gate.decide(heard, window(0.3f)))
    }

    @Test
    fun unmappedLabelsDoNotBlockAMappedOne() {
        val heard = listOf(Heard("Music", 0.95f), Heard("Speech", 0.55f))
        assertEquals(SoundType.SPEECH, gate.decide(heard, window(0.3f)))
    }

    @Test
    fun shortKnockInAQuietWindowStillPasses() {
        val samples = window(0f)
        for (i in 8_000 until 8_800) samples[i] = 0.3f
        assertEquals(SoundType.KNOCK, gate.decide(listOf(Heard("Knock", 0.7f)), samples))
    }

    @Test
    fun loudnessIsMeasuredInDbfs() {
        assertEquals(-20f, SoundGate.loudnessDbfs(window(0.1f)), 0.01f)
        assertEquals(Float.NEGATIVE_INFINITY, SoundGate.loudnessDbfs(window(0f)), 0f)
    }
}
