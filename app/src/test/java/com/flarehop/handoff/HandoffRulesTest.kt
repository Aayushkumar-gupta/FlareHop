package com.flarehop.handoff

import com.flarehop.flare.FlareConstants.STANDBY_INTERVAL_MS
import com.flarehop.flare.FlareConstants.TAKEOVER_SILENCE_MS
import com.flarehop.flare.PacketCodec
import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosPacket
import com.flarehop.flare.SosStatus
import com.flarehop.flare.SoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random

@Ignore("Waits for the handoff rules in RealFlareEngine (Build list: Handoff rules)")
class HandoffRulesTest {
    private val radio = FakeRadio()

    private val details = SosDetails(
        people = 2,
        injured = true,
        trapped = true,
        water = false,
        medical = false,
        fire = false,
    )

    private fun phone(name: String, role: Role, seed: Int, battery: Int): RealFlareEngine =
        radio.add(name, RealFlareEngine(clock = radio.clock, random = Random(seed)))
            .also {
                it.setRole(role)
                it.setBattery(battery)
            }

    private fun RealFlareEngine.status() = mySos.value?.status

    private fun sendAndLetStandbysJoin(victim: RealFlareEngine) {
        victim.sendSos(details, fix = null)
        radio.run(2 * STANDBY_INTERVAL_MS)
    }

    @Test
    fun nearbyPhoneJoinsAsStandby() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 80)
        sendAndLetStandbysJoin(a)
        assertEquals(SosStatus.STANDING_BY, b.status())
        assertEquals(a.mySos.value!!.record.messageId, b.mySos.value!!.record.messageId)
    }

    @Test
    fun carrierListsTheStandbyInItsGroup() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 80)
        sendAndLetStandbysJoin(a)
        assertTrue(a.group.value.any { it.phoneId == b.phoneId && !it.isCarrier })
    }

    @Test
    fun weakSignalDoesNotJoin() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 80)
        radio.signal("A", "B", dbm = -80)
        sendAndLetStandbysJoin(a)
        assertNull(b.mySos.value)
    }

    @Test
    fun rescuerNeverJoins() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val c = phone("C", Role.RESCUER, seed = 3, battery = 80)
        sendAndLetStandbysJoin(a)
        assertNull(c.mySos.value)
    }

    @Test
    fun lowBatteryHandsOffToTheStandby() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 80)
        sendAndLetStandbysJoin(a)
        val sos = a.mySos.value!!.record
        a.setBattery(9)
        radio.run(3_000)
        val carried = b.mySos.value!!
        assertEquals(SosStatus.SENDING, carried.status)
        assertEquals(sos.messageId, carried.record.messageId)
        assertEquals(a.phoneId, carried.record.originId)
        assertEquals(b.phoneId, carried.record.carrierId)
        assertEquals(SosStatus.HANDED_OFF, a.status())
    }

    @Test
    fun rescuerFollowsTheNewCarrier() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 80)
        val c = phone("C", Role.RESCUER, seed = 3, battery = 90)
        sendAndLetStandbysJoin(a)
        a.setBattery(9)
        radio.run(3_000)
        assertEquals(b.phoneId, c.knownSos.value.single().carrierId)
    }

    @Test
    fun handoffPicksTheStandbyWithMoreBattery() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 40)
        val d = phone("D", Role.NEARBY, seed = 4, battery = 70)
        sendAndLetStandbysJoin(a)
        a.setBattery(9)
        radio.run(3_000)
        assertEquals(SosStatus.SENDING, d.status())
        assertEquals(SosStatus.STANDING_BY, b.status())
    }

    @Test
    fun standbyTakesOverAfterSilence() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 80)
        sendAndLetStandbysJoin(a)
        radio.switchOff("A")
        radio.run(TAKEOVER_SILENCE_MS - 1_000)
        assertEquals(SosStatus.STANDING_BY, b.status())
        radio.run(2_000)
        assertEquals(SosStatus.SENDING, b.status())
    }

    @Test
    fun standbyWithLessBatteryWaitsLonger() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 80)
        val d = phone("D", Role.NEARBY, seed = 4, battery = 40)
        sendAndLetStandbysJoin(a)
        radio.switchOff("A")
        radio.run(TAKEOVER_SILENCE_MS + 1_000)
        assertEquals(SosStatus.SENDING, b.status())
        radio.run(10_000)
        assertEquals(SosStatus.STANDING_BY, d.status())
    }

    @Test
    fun weakerOfTwoCarriersBacksOff() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 30)
        sendAndLetStandbysJoin(a)
        radio.switchOff("A")
        radio.run(TAKEOVER_SILENCE_MS + 1_000)
        val sos = b.mySos.value!!.record
        val rival = if (b.phoneId == 999) 998 else 999
        val strongerCopy = SosPacket(
            messageId = sos.messageId,
            originId = sos.originId,
            carrierId = rival,
            sound = SoundType.NONE,
            soundAgeMin = null,
            latitude = null,
            longitude = null,
            accuracyM = null,
            fixAgeMin = null,
            details = details,
            battery = 90,
        )
        b.onPacket(PacketCodec.encode(strongerCopy), rssi = -60, atMs = radio.now)
        assertEquals(SosStatus.STANDING_BY, b.status())
    }

    @Test
    fun cancelReleasesTheStandby() {
        val a = phone("A", Role.VICTIM, seed = 1, battery = 50)
        val b = phone("B", Role.NEARBY, seed = 2, battery = 80)
        sendAndLetStandbysJoin(a)
        a.cancelSos()
        radio.run(1_000)
        assertNull(b.mySos.value)
    }
}
