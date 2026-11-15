package com.flarehop.ui

import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundTextTest {
    private val copyAt = 100_000L

    private fun record(sound: SoundType, soundAgeSec: Int?) = SosRecord(
        messageId = 7L,
        originId = 0x1A2B,
        carrierId = 0x1A2B,
        latitude = null,
        longitude = null,
        accuracyM = null,
        fixAgeMin = null,
        people = 0,
        injured = false,
        trapped = false,
        water = false,
        medical = false,
        fire = false,
        battery = 40,
        sound = sound,
        soundAgeSec = soundAgeSec,
        firstHeardAt = 1_000L,
        lastHeardAt = copyAt,
        lastRssi = -60,
        acked = true,
        cancelled = false,
    )

    @Test
    fun aSoundHeardSecondsAgoShowsItsSeconds() {
        assertEquals("Knocking, 12 s ago", soundText(record(SoundType.KNOCK, 12), copyAt))
    }

    @Test
    fun theAgeKeepsCountingBetweenPackets() {
        val knock = record(SoundType.KNOCK, 12)
        assertEquals("Knocking, 20 s ago", soundText(knock, copyAt + 8_000L))
    }

    @Test
    fun aFreshSoundIsJustNow() {
        assertEquals("Speech, just now", soundText(record(SoundType.SPEECH, 2), copyAt))
    }

    @Test
    fun olderSoundsShowMinutes() {
        assertEquals("Crying, 3 min ago", soundText(record(SoundType.CRYING, 190), copyAt))
    }

    @Test
    fun noSoundSaysSo() {
        assertEquals("No sound detected yet", soundText(record(SoundType.NONE, null), copyAt))
    }
}
