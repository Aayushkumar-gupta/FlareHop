package com.flarehop.ui

import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SosHistoryTest {
    private val history = SosHistory()

    private fun record(
        carrierId: Int = 0x1A2B,
        battery: Int? = 40,
        rssi: Int? = -60,
        sound: SoundType = SoundType.NONE,
        soundAgeMin: Int? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyM: Int? = null,
        heardAt: Long = 1_000L,
        cancelled: Boolean = false,
    ) = SosRecord(
        messageId = 7L,
        originId = 0x1A2B,
        carrierId = carrierId,
        latitude = latitude,
        longitude = longitude,
        accuracyM = accuracyM,
        fixAgeMin = latitude?.let { 0 },
        people = 0,
        injured = false,
        trapped = false,
        water = false,
        medical = false,
        fire = false,
        battery = battery,
        sound = sound,
        soundAgeMin = soundAgeMin,
        firstHeardAt = 1_000L,
        lastHeardAt = heardAt,
        lastRssi = rssi,
        acked = true,
        cancelled = cancelled,
    )

    private fun texts(vararg steps: SosRecord): List<String> =
        steps.map { history.update(listOf(it)) }.last().getValue(7L).history.map { it.text }

    @Test
    fun theFirstPacketIsLoggedWithItsPhoneAndBattery() {
        assertEquals(listOf("First heard from the victim's phone 1A2B (40%)"), texts(record()))
    }

    @Test
    fun aRepeatedPacketAddsNothing() {
        assertEquals(1, texts(record(), record(heardAt = 2_000L)).size)
    }

    @Test
    fun aNewCarrierIsLoggedAndCountedAsASecondPhone() {
        val track = listOf(
            record(),
            record(carrierId = 0x3C4D, battery = 80, rssi = -55, heardAt = 5_000L),
        ).map { history.update(listOf(it)) }.last().getValue(7L)

        assertEquals("Now carried by phone 3C4D (80%)", track.history.last().text)
        assertEquals(listOf(0x1A2B, 0x3C4D), track.carriers.map { it.phoneId })
        val carrier = track.carriers.last()
        assertEquals(80, carrier.battery)
        assertEquals(-55, carrier.rssi)
        assertEquals(5_000L, carrier.lastHeardAt)
        assertTrue(track.carriers.first().isVictim)
    }

    @Test
    fun theVictimComingBackIsLogged() {
        val log = texts(
            record(),
            record(carrierId = 0x3C4D, heardAt = 2_000L),
            record(heardAt = 3_000L),
        )
        assertEquals("The victim's phone 1A2B is sending again", log.last())
    }

    @Test
    fun aSoundIsLoggedWhenItIsNewOrHeardAgain() {
        val log = texts(
            record(),
            record(sound = SoundType.KNOCK, soundAgeMin = 0, heardAt = 2_000L),
            record(sound = SoundType.KNOCK, soundAgeMin = 1, heardAt = 62_000L),
            record(sound = SoundType.KNOCK, soundAgeMin = 0, heardAt = 63_000L),
        )
        assertEquals(listOf("Knocking heard", "Knocking heard"), log.drop(1))
    }

    @Test
    fun aFirstFixIsLoggedAndOnlyARealMoveAfterIt() {
        val log = texts(
            record(),
            record(latitude = 32.7277, longitude = -97.1120, accuracyM = 12, heardAt = 2_000L),
            record(latitude = 32.72775, longitude = -97.1120, accuracyM = 12, heardAt = 3_000L),
            record(latitude = 32.7282, longitude = -97.1120, accuracyM = 12, heardAt = 4_000L),
        )
        assertEquals("GPS position received (±12 m)", log[1])
        assertEquals(3, log.size)
        assertTrue(log.last().startsWith("Position moved "))
    }

    @Test
    fun batteryIsLoggedInTenPercentSteps() {
        val log = texts(
            record(battery = 40),
            record(battery = 38, heardAt = 2_000L),
            record(battery = 31, heardAt = 3_000L),
            record(battery = 29, heardAt = 4_000L),
        )
        assertEquals(listOf("Battery 38%", "Battery 29%"), log.drop(1))
    }

    @Test
    fun aCancelIsLogged() {
        val log = texts(record(), record(cancelled = true, heardAt = 2_000L))
        assertEquals("Cancelled by the victim", log.last())
    }

    @Test
    fun onlyTheNewestEntriesAreKept() {
        val steps = (0..SosHistory.MAX_ENTRIES * 2).map {
            record(carrierId = if (it % 2 == 0) 0x1A2B else 0x3C4D, heardAt = 1_000L + it)
        }
        assertEquals(SosHistory.MAX_ENTRIES, texts(*steps.toTypedArray()).size)
    }
}
