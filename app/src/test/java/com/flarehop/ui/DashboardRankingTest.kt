package com.flarehop.ui

import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRankingTest {
    private val now = 1_000_000L

    private fun sos(
        origin: Int,
        heardAt: Long = now,
        firstHeardAt: Long = heardAt,
        messageId: Long = origin.toLong(),
        cancelled: Boolean = false,
    ) = SosRecord(
        messageId = messageId,
        originId = origin,
        carrierId = origin,
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
        battery = 50,
        sound = SoundType.NONE,
        soundAgeMin = null,
        firstHeardAt = firstHeardAt,
        lastHeardAt = heardAt,
        lastRssi = -60,
        acked = false,
        cancelled = cancelled,
    )

    @Test
    fun anSosHeardJustNowIsLive() {
        val ranking = rankForDashboard(listOf(sos(1)), now)
        assertEquals(listOf(1), ranking.active.map { it.originId })
        assertTrue(ranking.stale.isEmpty())
    }

    @Test
    fun anSosNotHeardForTwoMinutesMovesBelowTheLiveOnes() {
        val old = sos(1, heardAt = now - STALE_AFTER_MS)
        val ranking = rankForDashboard(listOf(old, sos(2)), now)
        assertEquals(listOf(2), ranking.active.map { it.originId })
        assertEquals(listOf(1), ranking.stale.map { it.originId })
    }

    @Test
    fun justUnderTwoMinutesIsStillLive() {
        assertFalse(isStale(sos(1, heardAt = now - STALE_AFTER_MS + 1), now))
        assertTrue(isStale(sos(1, heardAt = now - STALE_AFTER_MS), now))
    }

    @Test
    fun staleOnesAreMostRecentlyHeardFirst() {
        val older = sos(1, heardAt = now - 10 * 60_000L)
        val newer = sos(2, heardAt = now - 3 * 60_000L)
        val ranking = rankForDashboard(listOf(older, newer), now)
        assertEquals(listOf(2, 1), ranking.stale.map { it.originId })
    }

    @Test
    fun aCancelledSosStaysWithTheCancelledOnesEvenWhenOld() {
        val cancelled = sos(1, heardAt = now - 10 * 60_000L, cancelled = true)
        val ranking = rankForDashboard(listOf(cancelled), now)
        assertTrue(ranking.stale.isEmpty())
        assertEquals(listOf(1), ranking.cancelled.map { it.originId })
    }

    @Test
    fun onlyTheNewestSosPerVictimIsShown() {
        val first = sos(1, heardAt = now - 5 * 60_000L, firstHeardAt = now - 9 * 60_000L)
        val second = sos(1, messageId = 99L, firstHeardAt = now - 60_000L)
        val ranking = rankForDashboard(listOf(first, second), now)
        assertEquals(listOf(99L), ranking.active.map { it.messageId })
        assertTrue(ranking.stale.isEmpty())
    }
}
