package com.flarehop.sense

import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Test

class PriorityScoreTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun record(
        people: Int = 0,
        injured: Boolean = false,
        trapped: Boolean = false,
        water: Boolean = false,
        battery: Int? = null,
        sound: SoundType = SoundType.NONE,
        soundAgeSec: Int? = null,
        firstHeardAt: Long = now,
        lastHeardAt: Long = now,
        messageId: Long = 1L,
    ) = SosRecord(
        messageId = messageId,
        originId = 1,
        carrierId = 1,
        latitude = null,
        longitude = null,
        accuracyM = null,
        fixAgeMin = null,
        people = people,
        injured = injured,
        trapped = trapped,
        water = water,
        medical = false,
        fire = false,
        battery = battery,
        sound = sound,
        soundAgeSec = soundAgeSec,
        firstHeardAt = firstHeardAt,
        lastHeardAt = lastHeardAt,
        lastRssi = null,
        acked = false,
        cancelled = false,
    )

    private fun check(record: SosRecord, expectedScore: Int, expectedBand: PriorityBand) {
        val score = PriorityScore.score(record, now)
        assertEquals(expectedScore, score)
        assertEquals(expectedBand, PriorityScore.band(score))
    }

    @Test
    fun trappedInjuredTwoPeopleIsCritical80() = check(
        record(people = 2, injured = true, trapped = true, battery = 50),
        80,
        PriorityBand.CRITICAL,
    )

    @Test
    fun trappedTwoPeopleKnockingIsCritical70() = check(
        record(
            people = 2,
            trapped = true,
            battery = 60,
            sound = SoundType.KNOCK,
            soundAgeSec = 120,
        ),
        70,
        PriorityBand.CRITICAL,
    )

    @Test
    fun waterFourPeopleLowBatteryAfter30MinIsHigh61() = check(
        record(people = 4, water = true, battery = 15, firstHeardAt = now - 30 * minute),
        61,
        PriorityBand.HIGH,
    )

    @Test
    fun onePersonNoFlagsIsNormal5() = check(record(people = 1), 5, PriorityBand.NORMAL)

    @Test
    fun crowdNoiseAddsNothing() {
        val crowd = record(sound = SoundType.CROWD, soundAgeSec = 0)
        assertEquals(0, PriorityScore.score(crowd, now))
    }

    @Test
    fun soundStopsCountingOnceOlderThanTenMinutes() {
        val knock = record(
            sound = SoundType.KNOCK,
            soundAgeSec = 480,
            lastHeardAt = now - 3 * minute,
        )
        assertEquals(0, PriorityScore.score(knock, now))
    }

    @Test
    fun equalScoresRankTheEarlierSosFirst() {
        val later = record(people = 1, messageId = 2L, firstHeardAt = now - minute)
        val earlier = record(people = 1, messageId = 3L, firstHeardAt = now - 2 * minute)
        val ranked = listOf(later, earlier).sortedWith(PriorityScore.mostUrgentFirst(now))
        assertEquals(listOf(3L, 2L), ranked.map { it.messageId })
    }
}
