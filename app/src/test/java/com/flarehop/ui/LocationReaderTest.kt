package com.flarehop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationReaderTest {
    private val fiveSeconds = 5_000L

    @Test
    fun newerGpsFixWinsEvenWhenSlightlyLessAccurate() {
        assertTrue(fixIsBetter(fiveSeconds, sameSource = true, 12f, 8f))
    }

    @Test
    fun olderFixFromTheSameSourceDoesNotWin() {
        assertFalse(fixIsBetter(-fiveSeconds, sameSource = true, 5f, 8f))
    }

    @Test
    fun roughNetworkFixCannotReplaceAFreshGpsFix() {
        assertFalse(fixIsBetter(fiveSeconds, sameSource = false, 100f, 8f))
    }

    @Test
    fun moreAccurateFixFromAnotherSourceWins() {
        assertTrue(fixIsBetter(fiveSeconds, sameSource = false, 8f, 100f))
    }

    @Test
    fun aFixMoreThanTwoMinutesNewerAlwaysWins() {
        assertTrue(fixIsBetter(121_000L, sameSource = false, 100f, 8f))
    }

    @Test
    fun aFixMoreThanTwoMinutesOlderNeverWins() {
        assertFalse(fixIsBetter(-121_000L, sameSource = true, 3f, 100f))
    }
}
