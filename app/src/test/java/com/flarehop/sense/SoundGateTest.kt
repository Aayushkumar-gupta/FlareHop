package com.flarehop.sense

import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundGateTest {
    private val gate = SoundGate()

    private fun window(level: Float, size: Int = 16_000) = FloatArray(size) { level }

    @Test
    fun everyContractClassMapsToItsSoundType() {
        val expected = mapOf(
            "Speech" to SoundType.SPEECH,
            "Child speech, kid speaking" to SoundType.SPEECH,
            "Conversation" to SoundType.SPEECH,
            "Narration, monologue" to SoundType.SPEECH,
            "Whispering" to SoundType.SPEECH,
            "Shout" to SoundType.SHOUT,
            "Bellow" to SoundType.SHOUT,
            "Whoop" to SoundType.SHOUT,
            "Yell" to SoundType.SHOUT,
            "Screaming" to SoundType.SHOUT,
            "Children shouting" to SoundType.SHOUT,
            "Knock" to SoundType.KNOCK,
            "Tap" to SoundType.KNOCK,
            "Wood" to SoundType.KNOCK,
            "Thump, thud" to SoundType.KNOCK,
            "Bang" to SoundType.KNOCK,
            "Door" to SoundType.KNOCK,
            "Wood block" to SoundType.KNOCK,
            "Crying, sobbing" to SoundType.CRYING,
            "Baby cry, infant cry" to SoundType.CRYING,
            "Whimper" to SoundType.CRYING,
            "Wail, moan" to SoundType.CRYING,
            "Groan" to SoundType.CRYING,
            "Chatter" to SoundType.CROWD,
            "Crowd" to SoundType.CROWD,
            "Hubbub, speech noise, speech babble" to SoundType.CROWD,
            "Cheering" to SoundType.CROWD,
            "Applause" to SoundType.CROWD,
        )
        expected.forEach { (label, type) -> assertEquals(label, type, SoundGate.typeFor(label)) }
    }

    @Test
    fun crowdLouderThanSpeechIsCrowd() {
        val heard = listOf(Heard("Speech", 0.55f), Heard("Crowd", 0.7f))
        assertEquals(SoundType.CROWD, gate.decide(heard, window(0.3f)))
    }

    @Test
    fun onlyCrowdAndNoneAreNotSignsOfLife() {
        val notAlive = SoundType.entries.filterNot { it.isSignOfLife }
        assertEquals(listOf(SoundType.NONE, SoundType.CROWD), notAlive)
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
    fun scoreBelowMinimumIsIgnored() {
        assertNull(gate.decide(listOf(Heard("Knock", 0.29f)), window(0.3f)))
    }

    @Test
    fun scoreOfExactlyTheMinimumCounts() {
        assertEquals(SoundType.SPEECH, gate.decide(listOf(Heard("Speech", 0.3f)), window(0.3f)))
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

    // Seconds from the Galaxy A15 and Motorola logs that the old -45 dBFS floor dropped.
    @Test
    fun knockLabelsAddUp() {
        val heard = listOf(Heard("Knock", 0.26f), Heard("Door", 0.2f))
        assertEquals(SoundType.KNOCK, gate.decide(heard, window(0.3f)))
    }

    @Test
    fun aLabelInBothChunksOfASecondCountsOnce() {
        val heard = listOf(Heard("Knock", 0.2f), Heard("Knock", 0.15f))
        assertNull(gate.decide(heard, window(0.3f)))
    }

    @Test
    fun knockAndVoiceAtTheLevelsThePhonesRecordBothCount() {
        val fiftyBelowFullScale = window(0.0032f)
        val knock = listOf(Heard("Knock", 0.41f), Heard("Door", 0.33f))
        val voice = listOf(Heard("Speech", 0.89f))
        assertEquals(SoundType.KNOCK, gate.decide(knock, fiftyBelowFullScale))
        assertEquals(SoundType.SPEECH, gate.decide(voice, fiftyBelowFullScale))
    }

    @Test
    fun knockQuieterThanTheFloorIsIgnored() {
        assertNull(gate.decide(listOf(Heard("Knock", 0.9f)), window(0.001f)))
    }

    @Test
    fun otherSoundsAreNotAddedUp() {
        val heard = listOf(Heard("Speech", 0.2f), Heard("Conversation", 0.2f))
        assertNull(gate.decide(heard, window(0.3f)))
    }

    @Test
    fun speechStillWinsOverAWeakerKnock() {
        val heard = listOf(Heard("Speech", 0.8f), Heard("Knock", 0.3f), Heard("Tap", 0.2f))
        assertEquals(SoundType.SPEECH, gate.decide(heard, window(0.3f)))
    }

    @Test
    fun loudnessIsMeasuredInDbfs() {
        assertEquals(-20f, SoundGate.loudnessDbfs(window(0.1f)), 0.01f)
        assertEquals(Float.NEGATIVE_INFINITY, SoundGate.loudnessDbfs(window(0f)), 0f)
    }
}
