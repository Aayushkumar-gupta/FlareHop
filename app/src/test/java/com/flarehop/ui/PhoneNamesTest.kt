package com.flarehop.ui

import com.flarehop.flare.DirectSignal
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PhoneNamesTest {

    private fun record(
        originId: Int,
        firstHeardAt: Long,
        carrierId: Int = originId,
        messageId: Long = originId.toLong(),
    ) = SosRecord(
        messageId = messageId,
        originId = originId,
        carrierId = carrierId,
        latitude = null,
        longitude = null,
        accuracyM = null,
        fixAgeMin = null,
        people = 1,
        injured = false,
        trapped = false,
        water = false,
        medical = false,
        fire = false,
        battery = null,
        sound = SoundType.NONE,
        soundAgeMin = null,
        firstHeardAt = firstHeardAt,
        lastHeardAt = firstHeardAt,
        lastRssi = -60,
        acked = false,
        cancelled = false,
    )

    @Test
    fun survivorsAreNumberedInTheOrderFirstHeard() {
        val names = PhoneNames().update(
            listOf(record(0xBEEF, firstHeardAt = 200), record(0x1234, firstHeardAt = 100)),
            otherPhones = emptyList(),
            myIds = emptySet(),
        )
        assertEquals("Survivor 1", names.survivor(0x1234))
        assertEquals("Survivor 2", names.survivor(0xBEEF))
    }

    @Test
    fun aNumberNeverChangesOnceGiven() {
        val first = PhoneNames().update(listOf(record(0xAAAA, 500)), emptyList(), emptySet())
        val later = first.update(
            listOf(record(0xBBBB, 100), record(0xAAAA, 500)),
            emptyList(),
            emptySet(),
        )
        assertEquals("Survivor 1", later.survivor(0xAAAA))
        assertEquals("Survivor 2", later.survivor(0xBBBB))
    }

    @Test
    fun aSurvivorStaysTheSameAfterSendingANewSos() {
        val names = PhoneNames()
            .update(listOf(record(0xAAAA, 100, messageId = 1)), emptyList(), emptySet())
            .update(listOf(record(0xAAAA, 900, messageId = 2)), emptyList(), emptySet())
        assertEquals("Survivor 1", names.survivor(0xAAAA))
        assertEquals("Survivor 1's SOS", names.sos(2))
    }

    @Test
    fun phonesWithoutAnSosGetTheirOwnNumbers() {
        val names = PhoneNames().update(
            listOf(record(0xAAAA, 100, carrierId = 0xCCCC)),
            otherPhones = listOf(0xDDDD),
            myIds = emptySet(),
        )
        assertEquals("Phone 1", names.phone(0xCCCC))
        assertEquals("Phone 2", names.phone(0xDDDD))
        assertEquals("Survivor 1", names.phone(0xAAAA))
    }

    @Test
    fun thisPhoneIsNeverNumbered() {
        val names = PhoneNames().update(
            listOf(record(0x1111, 100), record(0x2222, 200)),
            emptyList(),
            myIds = setOf(0x1111),
        )
        assertEquals("You", names.survivor(0x1111))
        assertEquals("This phone", names.phone(0x1111))
        assertEquals("Survivor 1", names.survivor(0x2222))
    }

    @Test
    fun nothingNewKeepsTheSameObject() {
        val names = PhoneNames().update(listOf(record(0xAAAA, 100)), emptyList(), emptySet())
        assertSame(names, names.update(listOf(record(0xAAAA, 100)), emptyList(), emptySet()))
    }

    @Test
    fun signalIsTheMedianSoOneSpikeDoesNotMoveIt() {
        val smoother = SignalSmoother()
        listOf(-70, -71, -69, -40, -70).forEach { smoother.add(signal(it)) }
        assertEquals(-70, smoother.add(signal(-70)))
    }

    @Test
    fun signalStartsAgainForANewCarrier() {
        val smoother = SignalSmoother()
        repeat(5) { smoother.add(signal(-50)) }
        assertEquals(-85, smoother.add(signal(-85, carrierId = 9)))
    }

    @Test
    fun signalKeepsOnlyTheLastTenReadings() {
        val smoother = SignalSmoother()
        repeat(10) { smoother.add(signal(-40)) }
        var last = 0
        repeat(10) { last = smoother.add(signal(-80)) }
        assertEquals(-80, last)
    }

    @Test
    fun heardTimeMovesInTenSecondSteps() {
        assertEquals("just now", agoText(0))
        assertEquals("just now", agoText(9_999))
        assertEquals("10s ago", agoText(19_999))
        assertEquals("50s ago", agoText(59_000))
        assertEquals("2 min ago", agoText(150_000))
    }

    private fun signal(rssi: Int, carrierId: Int = 7) =
        DirectSignal(messageId = 1L, carrierId = carrierId, rssi = rssi, atMs = 0L)
}
