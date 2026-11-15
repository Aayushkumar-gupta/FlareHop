package com.flarehop.flare

import com.flarehop.handoff.FakeRadio
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshConflictTest {
    private val radio = FakeRadio()

    private val details = SosDetails(
        people = 1,
        injured = true,
        trapped = false,
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

    private fun sendAndSettle(victim: RealFlareEngine) {
        victim.sendSos(details, fix = null)
        radio.run(6_000)
    }

    // B and D cannot hear each other while A goes silent, so both take A's SOS over.
    private fun twoCarriers(
        bBattery: Int,
        dBattery: Int,
    ): Triple<RealFlareEngine, RealFlareEngine, RealFlareEngine> {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = bBattery)
        val d = phone("D", seed = 4, battery = dBattery)
        radio.cutLink("B", "D")
        sendAndSettle(a)
        radio.switchOff("A")
        radio.run(25_000)
        assertEquals(1, b.carried.value.size)
        assertEquals(1, d.carried.value.size)
        return Triple(a, b, d)
    }

    @Test
    fun theCarrierWithLessBatteryStopsAndKeepsItsCopy() {
        val (a, b, d) = twoCarriers(bBattery = 60, dBattery = 80)

        radio.connect("B", "D")
        radio.run(3_000)

        assertTrue(b.carried.value.isEmpty())
        assertEquals(1, d.carried.value.size)
        val kept = b.knownSos.value.single()
        assertEquals(a.phoneId, kept.originId)
        assertEquals(d.phoneId, kept.carrierId)
    }

    @Test
    fun onEqualBatteryTheHigherPhoneIdStops() {
        val (_, b, d) = twoCarriers(bBattery = 70, dBattery = 70)

        radio.connect("B", "D")
        radio.run(3_000)

        val (lower, higher) = if (b.phoneId < d.phoneId) b to d else d to b
        assertEquals(1, lower.carried.value.size)
        assertTrue(higher.carried.value.isEmpty())
    }

    @Test
    fun theVictimComingBackKeepsItsOwnSos() {
        val a = phone("A", seed = 1, battery = 30)
        val b = phone("B", seed = 2, battery = 90)
        sendAndSettle(a)
        radio.cutLink("A", "B")
        radio.run(17_000)
        assertEquals(1, b.carried.value.size)

        radio.connect("A", "B")
        radio.run(3_000)

        assertTrue(b.carried.value.isEmpty())
        assertEquals(SosStatus.SENDING, a.mySos.value!!.status)
    }

    @Test
    fun thePhoneThatStoppedTakesOverAgainWhenTheOtherDies() {
        val (a, b, d) = twoCarriers(bBattery = 60, dBattery = 80)
        radio.connect("B", "D")
        radio.run(3_000)
        assertTrue(b.carried.value.isEmpty())

        radio.switchOff("D")
        radio.run(17_000)

        assertEquals(a.phoneId, b.carried.value.single().originId)
    }

    @Test
    fun theRescuerEndsUpHearingOneCarrier() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 60)
        val d = phone("D", seed = 4, battery = 80)
        val r = phone("R", seed = 5, battery = 100, role = Role.RESCUER)
        radio.cutLink("B", "D")
        radio.cutLink("D", "R")
        sendAndSettle(a)
        radio.switchOff("A")
        radio.run(25_000)
        assertEquals(b.phoneId, r.knownSos.value.single().carrierId)

        radio.connect("B", "D")
        radio.connect("D", "R")
        radio.run(5_000)

        assertTrue(b.carried.value.isEmpty())
        assertEquals(d.phoneId, r.knownSos.value.single().carrierId)
    }
}
