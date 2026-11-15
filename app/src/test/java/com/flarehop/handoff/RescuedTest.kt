package com.flarehop.handoff

import com.flarehop.flare.FlareConstants.RESCUE_CONFIRM_MS
import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosStatus
import com.flarehop.ui.phonesWithoutSos
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Rule 12: a rescuer who reached someone marks them rescued, and their SOS stops everywhere.
class RescuedTest {
    private val radio = FakeRadio()
    private val none = SosDetails(0, false, false, false, false, false)

    private fun phone(name: String, seed: Int, battery: Int, role: Role = Role.SURVIVOR) =
        radio.add(name, RealFlareEngine(clock = radio.clock, random = Random(seed))).also {
            it.setRole(role)
            it.setBattery(battery)
        }

    private val a = phone("A", 1, 60)
    private val b = phone("B", 2, 80)
    private val c = phone("C", 3, 100, Role.RESCUER)

    private fun aTapsSos(): Long {
        a.sendSos(none, fix = null)
        radio.run(6_000)
        return a.mySos.value!!.record.messageId
    }

    @Test
    fun theVictimsPhoneStopsCallingForHelp() {
        val sos = aTapsSos()
        c.markRescued(sos)
        radio.run(3_000)

        val mine = a.mySos.value!!
        assertEquals(SosStatus.RESCUED, mine.status)
        assertEquals(c.phoneId, mine.rescuedBy)
        assertTrue(c.knownSos.value.single().rescued)

        val heardAt = c.knownSos.value.single().lastHeardAt
        radio.run(5_000)
        assertEquals(heardAt, c.knownSos.value.single().lastHeardAt)
    }

    @Test
    fun anotherRescuerInRangeSeesItRescued() {
        val other = phone("R", 5, 90, Role.RESCUER)
        val sos = aTapsSos()
        c.markRescued(sos)
        radio.run(3_000)
        assertTrue(other.knownSos.value.single().rescued)
    }

    @Test
    fun aCarrierDropsItsCopyAndNoOneTakesItOverAgain() {
        val sos = aTapsSos()
        radio.switchOff("A")
        radio.run(20_000)
        assertEquals(1, b.carried.value.size)

        c.markRescued(sos)
        radio.run(30_000)

        assertTrue(b.carried.value.isEmpty())
        assertTrue(b.knownSos.value.single().rescued)
    }

    @Test
    fun onlyARescuerCanMarkSomeoneRescued() {
        val sos = aTapsSos()
        b.markRescued(sos)
        radio.run(3_000)
        assertEquals(SosStatus.SENDING, a.mySos.value!!.status)
        assertFalse(c.knownSos.value.single().rescued)
    }

    @Test
    fun theVictimCanCallForHelpAgain() {
        val sos = aTapsSos()
        c.markRescued(sos)
        radio.run(3_000)

        a.sendSos(none, fix = null)
        radio.run(3_000)

        val again = c.knownSos.value.single { !it.rescued }
        assertNotEquals(sos, again.messageId)
        assertEquals(a.phoneId, again.originId)
    }

    @Test
    fun noAnswerWithin45SecondsCallsForHelpAgain() {
        val sos = aTapsSos()
        c.markRescued(sos)
        radio.run(3_000)
        val rescuedAt = a.mySos.value!!.rescuedAt!!

        radio.run(rescuedAt + RESCUE_CONFIRM_MS - radio.now - 1_000)
        assertEquals(SosStatus.RESCUED, a.mySos.value!!.status)

        radio.run(4_000)

        val again = a.mySos.value!!
        assertEquals(SosStatus.SENDING, again.status)
        assertNotEquals(sos, again.record.messageId)
        assertFalse(c.knownSos.value.single { it.messageId == again.record.messageId }.rescued)
    }

    @Test
    fun doneMeansTheyAreSafeForGood() {
        val sos = aTapsSos()
        c.markRescued(sos)
        radio.run(3_000)
        a.cancelSos()
        radio.run(RESCUE_CONFIRM_MS + 10_000)
        assertNull(a.mySos.value)
    }

    @Test
    fun becomingARescuerAgainStartsACleanPanel() {
        val d = phone("D", 4, 40)
        val sos = aTapsSos()
        c.startSosOn(d.phoneId)
        radio.run(3_000)
        c.markRescued(sos)
        c.stopSosOn(d.phoneId)
        radio.run(3_000)
        a.cancelSos()
        radio.run(3_000)
        c.setRole(Role.SURVIVOR)
        radio.run(20_000)

        b.sendSos(none, fix = null)
        c.setRole(Role.RESCUER)
        radio.run(6_000)

        assertEquals(setOf(b.phoneId), c.knownSos.value.map { it.originId }.toSet())
        assertTrue(c.knownSos.value.none { it.cancelled || it.rescued })
        assertEquals(
            setOf(a.phoneId, d.phoneId),
            phonesWithoutSos(c.group.value, c.knownSos.value).map { it.phoneId }.toSet(),
        )
    }

    @Test
    fun aQuietSosIsKeptWhenBecomingARescuerAgain() {
        aTapsSos()
        radio.switchOff("A")
        radio.switchOff("B")
        c.setRole(Role.SURVIVOR)
        radio.run(3_000)
        c.setRole(Role.RESCUER)
        assertEquals(setOf(a.phoneId), c.knownSos.value.map { it.originId }.toSet())
    }
}
