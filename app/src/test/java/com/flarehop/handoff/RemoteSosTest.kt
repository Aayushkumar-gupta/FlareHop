package com.flarehop.handoff

import com.flarehop.flare.CallPacket
import com.flarehop.flare.PacketCodec
import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosStatus
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Rule 11: three victims together, one taps SOS, and the rescuer starts or stops the others' SOS.
class RemoteSosTest {
    private val radio = FakeRadio()
    private val none = SosDetails(0, false, false, false, false, false)

    private fun phone(name: String, seed: Int, battery: Int, role: Role = Role.SURVIVOR) =
        radio.add(name, RealFlareEngine(clock = radio.clock, random = Random(seed))).also {
            it.setRole(role)
            it.setBattery(battery)
        }

    private val a = phone("A", 1, 60)
    private val b = phone("B", 2, 80)
    private val d = phone("D", 4, 40)
    private val c = phone("C", 3, 100, Role.RESCUER)

    private fun aTapsSos() {
        a.sendSos(none, fix = null)
        radio.run(10_000)
    }

    @Test
    fun onlyTheTappedPhoneCallsForHelpAtFirst() {
        aTapsSos()
        assertNull(b.mySos.value)
        assertNull(d.mySos.value)
        assertEquals(setOf(a.phoneId), c.knownSos.value.map { it.originId }.toSet())
        assertEquals(
            setOf(a.phoneId, b.phoneId, d.phoneId),
            c.group.value.map {
                it.phoneId
            }.toSet(),
        )
    }

    @Test
    fun theRescuerStartsTheSosOfTheChosenPhoneOnly() {
        aTapsSos()
        c.startSosOn(b.phoneId)
        assertEquals(setOf(b.phoneId), c.pendingCalls.value)
        radio.run(3_000)

        val mine = b.mySos.value!!
        assertEquals(SosStatus.SENDING, mine.status)
        assertEquals(c.phoneId, mine.startedBy)
        assertTrue(mine.record.startedRemotely)
        assertNull(d.mySos.value)
        val seen = c.knownSos.value.single { it.originId == b.phoneId }
        assertTrue(seen.startedRemotely)
        assertTrue(b.mySos.value!!.record.acked)
        assertFalse(c.knownSos.value.single { it.originId == a.phoneId }.startedRemotely)
        assertTrue(c.pendingCalls.value.isEmpty())
    }

    @Test
    fun everyPhoneInTheNetworkCanBeStarted() {
        aTapsSos()
        c.startSosOn(b.phoneId)
        c.startSosOn(d.phoneId)
        radio.run(3_000)
        assertEquals(
            setOf(a.phoneId, b.phoneId, d.phoneId),
            c.knownSos.value.map { it.originId }.toSet(),
        )
    }

    @Test
    fun aPhoneAlreadySendingKeepsItsOwnSos() {
        aTapsSos()
        val before = a.mySos.value!!
        c.startSosOn(a.phoneId)
        radio.run(3_000)
        assertEquals(before.record.messageId, a.mySos.value!!.record.messageId)
        assertNull(a.mySos.value!!.startedBy)
    }

    @Test
    fun aVictimCannotStartAnotherPhone() {
        aTapsSos()
        a.startSosOn(b.phoneId)
        radio.run(3_000)
        assertTrue(a.pendingCalls.value.isEmpty())
        assertNull(b.mySos.value)
    }

    @Test
    fun theRescuerStopsAnSosItStarted() {
        aTapsSos()
        c.startSosOn(b.phoneId)
        radio.run(3_000)
        c.stopSosOn(b.phoneId)
        assertEquals(setOf(b.phoneId), c.pendingCalls.value)
        radio.run(3_000)

        assertNull(b.mySos.value)
        assertTrue(c.knownSos.value.single { it.originId == b.phoneId }.cancelled)
        assertTrue(c.pendingCalls.value.isEmpty())
    }

    @Test
    fun anSosItsOwnerTappedCannotBeStopped() {
        aTapsSos()
        val before = a.mySos.value!!.record.messageId
        c.stopSosOn(a.phoneId)
        radio.run(11_000)
        assertEquals(before, a.mySos.value!!.record.messageId)
        assertEquals(SosStatus.SENDING, a.mySos.value!!.status)
    }

    @Test
    fun aVictimCannotStopAnotherPhone() {
        aTapsSos()
        c.startSosOn(b.phoneId)
        radio.run(3_000)
        a.stopSosOn(b.phoneId)
        radio.run(3_000)
        assertEquals(SosStatus.SENDING, b.mySos.value!!.status)
    }

    @Test
    fun aPhoneCannotStartItself() {
        aTapsSos()
        b.startSosOn(b.phoneId)
        radio.run(3_000)
        assertTrue(b.pendingCalls.value.isEmpty())
        assertNull(b.mySos.value)
    }

    @Test
    fun aRescuerIsNeverStarted() {
        val other = phone("R", 5, 90, Role.RESCUER)
        aTapsSos()
        c.startSosOn(other.phoneId)
        radio.run(3_000)
        assertNull(other.mySos.value)
    }

    @Test
    fun aCancelledSosStaysCancelledUntilTheRescuerAsksAgain() {
        aTapsSos()
        c.startSosOn(b.phoneId)
        radio.run(3_000)
        b.cancelSos()
        radio.run(12_000)
        assertNull(b.mySos.value)

        c.startSosOn(b.phoneId)
        radio.run(3_000)
        assertEquals(SosStatus.SENDING, b.mySos.value!!.status)
    }

    @Test
    fun aPhoneOutOfRangeIsNotStartedAndTheRequestStops() {
        aTapsSos()
        radio.cutLink("C", "B")
        c.startSosOn(b.phoneId)
        radio.run(11_000)
        assertNull(b.mySos.value)
        assertTrue(c.pendingCalls.value.isEmpty())
    }

    @Test
    fun theStartedPhoneTakesOverLikeAnyOther() {
        aTapsSos()
        c.startSosOn(b.phoneId)
        radio.run(3_000)
        radio.switchOff("A")
        radio.run(20_000)
        assertEquals(setOf(a.phoneId), b.carried.value.map { it.originId }.toSet())
        assertEquals(SosStatus.SENDING, b.mySos.value!!.status)
    }

    // The rescuer may still be repeating its request when the owner cancels; the repeat must not
    // start the SOS again.
    @Test
    fun aRepeatOfTheSameRequestAfterCancelIsIgnored() {
        val request = PacketCodec.encode(
            CallPacket(callId = 99L, rescuerId = 0x5E6F, targetId = b.phoneId),
        )
        b.onPacket(request, rssi = -60, atMs = radio.now)
        assertEquals(SosStatus.SENDING, b.mySos.value!!.status)
        b.cancelSos()

        b.onPacket(request, rssi = -60, atMs = radio.now + 300)

        assertNull(b.mySos.value)
    }
}
