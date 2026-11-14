package com.flarehop.handoff

import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RescueLoopTest {
    private val radio = FakeRadio()

    private val details = SosDetails(
        people = 2,
        injured = true,
        trapped = true,
        water = false,
        medical = false,
        fire = false,
    )

    private fun phone(name: String, role: Role, seed: Int): RealFlareEngine =
        radio.add(name, RealFlareEngine(clock = radio.clock, random = Random(seed)))
            .also { it.setRole(role) }

    @Test
    fun rescuerHearsTheVictimsSos() {
        val a = phone("A", Role.VICTIM, seed = 1)
        val c = phone("C", Role.RESCUER, seed = 3)
        a.setBattery(60)
        a.sendSos(details, fix = null)
        radio.run(1_000)
        val heard = c.knownSos.value.single()
        assertEquals(a.mySos.value!!.record.messageId, heard.messageId)
        assertEquals(a.phoneId, heard.originId)
        assertEquals(a.phoneId, heard.carrierId)
        assertEquals(60, heard.battery)
    }

    @Test
    fun rescuerSeesTheSignalStrengthOfTheCarrier() {
        val a = phone("A", Role.VICTIM, seed = 1)
        val c = phone("C", Role.RESCUER, seed = 3)
        radio.signal("A", "C", dbm = -75)
        a.sendSos(details, fix = null)
        radio.run(1_000)
        assertEquals(-75, c.knownSos.value.single().lastRssi)
    }

    @Test
    fun victimLearnsARescuerReceivedTheSos() {
        val a = phone("A", Role.VICTIM, seed = 1)
        phone("C", Role.RESCUER, seed = 3)
        a.sendSos(details, fix = null)
        radio.run(2_000)
        assertTrue(a.mySos.value!!.record.acked)
    }

    @Test
    fun nearbyPhoneDoesNotSendAReceipt() {
        val a = phone("A", Role.VICTIM, seed = 1)
        phone("B", Role.NEARBY, seed = 2)
        a.sendSos(details, fix = null)
        radio.run(2_000)
        assertFalse(a.mySos.value!!.record.acked)
    }

    @Test
    fun phoneOutOfRangeHearsNothing() {
        val a = phone("A", Role.VICTIM, seed = 1)
        val c = phone("C", Role.RESCUER, seed = 3)
        radio.cutLink("A", "C")
        a.sendSos(details, fix = null)
        radio.run(2_000)
        assertTrue(c.knownSos.value.isEmpty())
        assertFalse(a.mySos.value!!.record.acked)
    }

    @Test
    fun cancelReachesTheRescuer() {
        val a = phone("A", Role.VICTIM, seed = 1)
        val c = phone("C", Role.RESCUER, seed = 3)
        a.sendSos(details, fix = null)
        radio.run(1_000)
        a.cancelSos()
        radio.run(1_000)
        assertTrue(c.knownSos.value.single().cancelled)
    }
}
