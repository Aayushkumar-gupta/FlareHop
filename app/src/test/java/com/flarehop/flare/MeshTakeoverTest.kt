package com.flarehop.flare

import com.flarehop.handoff.FakeRadio
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshTakeoverTest {
    private val radio = FakeRadio()

    private val details = SosDetails(
        people = 3,
        injured = false,
        trapped = true,
        water = true,
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

    @Test
    fun theTakenOverCopyKeepsTheVictimsDataAndSound() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        a.setSound(SoundType.SHOUT, radio.now)
        sendAndSettle(a)

        radio.switchOff("A")
        radio.run(17_000)

        val copy = b.carried.value.single()
        assertEquals(a.phoneId, copy.originId)
        assertEquals(b.phoneId, copy.carrierId)
        assertEquals(3, copy.people)
        assertTrue(copy.trapped)
        assertTrue(copy.water)
        assertEquals(SoundType.SHOUT, copy.sound)
    }

    @Test
    fun aRescuerNeverTakesOver() {
        val a = phone("A", seed = 1, battery = 50)
        val r = phone("R", seed = 5, battery = 100, role = Role.RESCUER)
        sendAndSettle(a)

        radio.switchOff("A")
        radio.run(40_000)

        assertTrue(r.carried.value.isEmpty())
    }

    @Test
    fun aPhoneBelowTheHandoffLevelNeverTakesOver() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 9)
        sendAndSettle(a)

        radio.switchOff("A")
        radio.run(40_000)

        assertTrue(b.carried.value.isEmpty())
    }

    @Test
    fun aCarrierHeardOnlyFarAwayIsNotTakenOver() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        radio.signal("A", "B", dbm = -80)
        sendAndSettle(a)

        radio.switchOff("A")
        radio.run(40_000)

        assertTrue(b.carried.value.isEmpty())
    }

    @Test
    fun aCancelledSosIsNeverTakenOver() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        sendAndSettle(a)

        a.cancelSos()
        radio.run(2_000)
        radio.switchOff("A")
        radio.run(40_000)

        assertTrue(b.carried.value.isEmpty())
    }

    @Test
    fun aCarrierThatDiesIsTakenOverToo() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 60)
        sendAndSettle(a)
        a.setBattery(9)
        radio.run(3_000)
        radio.switchOff("A")
        assertEquals(1, b.carried.value.size)

        radio.switchOff("B")
        radio.run(17_000)

        assertEquals(a.phoneId, d.carried.value.single().originId)
    }

    @Test
    fun aPhoneThatTookOverIsSeenAsTheCarrierByOthers() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val r = phone("R", seed = 5, battery = 100, role = Role.RESCUER)
        sendAndSettle(a)

        radio.switchOff("A")
        radio.run(18_000)

        val seen = r.knownSos.value.single()
        assertEquals(a.phoneId, seen.originId)
        assertEquals(b.phoneId, seen.carrierId)
        assertEquals(80, seen.battery)
    }

    @Test
    fun theSilentCarrierDoesNotCountAsStronger() {
        val a = phone("A", seed = 1, battery = 90)
        val b = phone("B", seed = 2, battery = 80)
        sendAndSettle(a)

        radio.switchOff("A")
        radio.run(17_000)

        assertEquals(1, b.carried.value.size)
    }

    @Test
    fun aWeakerPhoneWaitsFiveSecondsMorePerStrongerPhone() {
        // D is added first so it decides before B in every radio tick.
        val d = phone("D", seed = 4, battery = 40)
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        sendAndSettle(a)

        radio.switchOff("A")
        radio.run(17_000)

        assertEquals(1, b.carried.value.size)
        assertTrue(d.carried.value.isEmpty())
    }
}
