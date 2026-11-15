package com.flarehop.handoff

import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosStatus
import com.flarehop.flare.SoundType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

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

    private fun phone(name: String, seed: Int, battery: Int?, role: Role = Role.SURVIVOR) =
        radio.add(name, RealFlareEngine(clock = radio.clock, random = Random(seed)))
            .also { engine ->
                engine.setRole(role)
                battery?.let(engine::setBattery)
            }

    private fun send(victim: RealFlareEngine) {
        victim.sendSos(details, fix = null)
        radio.run(6_000)
    }

    @Test
    fun rule5LowBatteryHandsTheSosToThePhoneWithTheMostCharge() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 40)
        send(a)
        val sos = a.mySos.value!!.record

        a.setBattery(9)
        radio.run(3_000)

        assertEquals(SosStatus.HANDED_OFF, a.mySos.value!!.status)
        val copy = b.carried.value.single()
        assertEquals(sos.messageId, copy.messageId)
        assertEquals(a.phoneId, copy.originId)
        assertEquals(b.phoneId, copy.carrierId)
        assertTrue(d.carried.value.isEmpty())
    }

    @Test
    fun rule5TiesGoToTheLowestPhoneId() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 80)
        send(a)
        a.setBattery(9)
        radio.run(3_000)
        val (lower, higher) = if (b.phoneId < d.phoneId) b to d else d to b
        assertEquals(1, lower.carried.value.size)
        assertTrue(higher.carried.value.isEmpty())
    }

    @Test
    fun rule5OnlyAPhoneInRangeCanBeNamed() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 90)
        val d = phone("D", seed = 4, battery = 40)
        radio.signal("A", "B", dbm = -80)
        send(a)
        a.setBattery(9)
        radio.run(3_000)
        assertTrue(b.carried.value.isEmpty())
        assertEquals(1, d.carried.value.size)
    }

    @Test
    fun rule5ARescuerIsNeverNamed() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 60)
        val c = phone("C", seed = 3, battery = 100, role = Role.RESCUER)
        send(a)
        a.setBattery(9)
        radio.run(3_000)
        assertTrue(c.carried.value.isEmpty())
        assertEquals(1, b.carried.value.size)
    }

    @Test
    fun rule5APhoneWithUnknownBatteryIsNeverNamed() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = null)
        val d = phone("D", seed = 4, battery = 40)
        send(a)
        a.setBattery(9)
        radio.run(3_000)
        assertTrue(b.carried.value.isEmpty())
        assertEquals(1, d.carried.value.size)
    }

    @Test
    fun rule5NoAnswerWithin30SecondsNamesTheNextPhone() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 60)
        send(a)
        a.setBattery(9)
        radio.cutLink("A", "B")
        radio.run(25_000)
        assertTrue(d.carried.value.isEmpty())

        radio.run(10_000)

        assertTrue(b.carried.value.isEmpty())
        assertEquals(1, d.carried.value.size)
        assertEquals(SosStatus.HANDED_OFF, a.mySos.value!!.status)
    }

    @Test
    fun rule5ACarrierAboutToDieHandsOnEverythingItBroadcasts() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 60)
        send(a)
        b.sendSos(details, fix = null)
        radio.run(6_000)
        a.setBattery(9)
        radio.run(3_000)

        b.setBattery(8)
        radio.run(4_000)

        assertTrue(b.carried.value.isEmpty())
        assertEquals(SosStatus.HANDED_OFF, b.mySos.value!!.status)
        assertEquals(setOf(a.phoneId, b.phoneId), d.carried.value.map { it.originId }.toSet())
    }

    @Test
    fun rule6TheNewCarrierKeepsTheVictimsDataAndSendsItsOwnBattery() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val c = phone("C", seed = 3, battery = 100, role = Role.RESCUER)
        a.setSound(SoundType.KNOCK, radio.now)
        send(a)
        a.setBattery(9)
        radio.run(3_000)
        radio.switchOff("A")
        radio.run(2_000)

        val seen = c.knownSos.value.single()
        assertEquals(a.phoneId, seen.originId)
        assertEquals(b.phoneId, seen.carrierId)
        assertEquals(80, seen.battery)
        assertEquals(2, seen.people)
        assertTrue(seen.trapped)
        assertTrue(seen.injured)
        assertEquals(SoundType.KNOCK, seen.sound)
    }

    @Test
    fun rule6TheSosReachesPhonesTheVictimCannotReach() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val c = phone("C", seed = 3, battery = 100, role = Role.RESCUER)
        radio.cutLink("A", "C")
        send(a)
        assertTrue(c.knownSos.value.isEmpty())

        a.setBattery(9)
        radio.run(3_000)

        val seen = c.knownSos.value.single()
        assertEquals(a.phoneId, seen.originId)
        assertEquals(b.phoneId, seen.carrierId)
    }

    @Test
    @Ignore("Waits for rule 7, takeover after silence (step 3.3)")
    fun rule7TheStrongestPhoneTakesOverAfter15SecondsOfSilence() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        send(a)
        radio.switchOff("A")
        radio.run(14_000)
        assertTrue(b.carried.value.isEmpty())

        radio.run(3_000)

        assertEquals(a.phoneId, b.carried.value.single().originId)
    }

    @Test
    @Ignore("Waits for rule 7, takeover after silence (step 3.3)")
    fun rule7AWeakerPhoneWaitsLongerAndThenStandsDown() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 40)
        send(a)
        radio.switchOff("A")
        radio.run(17_000)
        assertEquals(1, b.carried.value.size)

        radio.run(15_000)

        assertTrue(d.carried.value.isEmpty())
    }

    @Test
    @Ignore("Waits for rule 8, two carriers for one SOS (step 3.3)")
    fun rule8OfTwoCarriersTheOneWithLessBatteryStops() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 60)
        radio.cutLink("B", "D")
        send(a)
        radio.switchOff("A")
        radio.run(25_000)
        assertEquals(1, b.carried.value.size)
        assertEquals(1, d.carried.value.size)

        radio.connect("B", "D")
        radio.run(3_000)

        assertEquals(1, b.carried.value.size)
        assertTrue(d.carried.value.isEmpty())
    }

    @Test
    fun rule10CancelReleasesEveryCopy() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val c = phone("C", seed = 3, battery = 100, role = Role.RESCUER)
        send(a)
        a.setBattery(9)
        radio.run(3_000)
        assertEquals(1, b.carried.value.size)

        a.cancelSos()
        radio.run(2_000)

        assertTrue(b.carried.value.isEmpty())
        assertTrue(c.knownSos.value.single().cancelled)
        assertNull(a.mySos.value)
    }

    @Test
    fun theLastPhoneStandingCarriesEverySos() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 40)
        val c = phone("C", seed = 3, battery = 90)
        val r = phone("R", seed = 5, battery = 100, role = Role.RESCUER)
        radio.cutLink("A", "R")
        radio.cutLink("B", "R")
        send(a)
        b.sendSos(details, fix = null)
        c.sendSos(details, fix = null)
        radio.run(6_000)

        a.setBattery(9)
        b.setBattery(9)
        radio.run(4_000)
        radio.switchOff("A")
        radio.switchOff("B")
        radio.run(3_000)

        assertEquals(setOf(a.phoneId, b.phoneId), c.carried.value.map { it.originId }.toSet())
        assertEquals(
            setOf(a.phoneId, b.phoneId, c.phoneId),
            r.knownSos.value.map { it.originId }.toSet(),
        )
        assertTrue(r.knownSos.value.all { it.carrierId == c.phoneId })
    }
}
