package com.flarehop.flare

import com.flarehop.handoff.FakeRadio
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SosFixTest {
    private val radio = FakeRadio()

    private val details = SosDetails(
        people = 0,
        injured = false,
        trapped = false,
        water = false,
        medical = false,
        fire = false,
    )

    private val fix = Fix(latitude = 51.5, longitude = -0.12, accuracyM = 8, fixAgeMin = 0)

    private fun phone(name: String, seed: Int, battery: Int?, role: Role = Role.SURVIVOR) =
        radio.add(name, RealFlareEngine(clock = radio.clock, random = Random(seed)))
            .also { engine ->
                engine.setRole(role)
                battery?.let(engine::setBattery)
            }

    @Test
    fun aFixThatArrivesAfterTheSosReachesTheRescuer() {
        val a = phone("A", seed = 1, battery = 50)
        val c = phone("C", seed = 3, battery = 100, role = Role.RESCUER)
        a.sendSos(details, fix = null)
        radio.run(3_000)
        val before = c.knownSos.value.single()
        assertNull(before.latitude)

        a.setFix(fix)
        radio.run(3_000)

        val seen = c.knownSos.value.single()
        assertEquals(before.messageId, seen.messageId)
        assertEquals(51.5, seen.latitude!!, 1e-6)
        assertEquals(-0.12, seen.longitude!!, 1e-6)
        assertEquals(8, seen.accuracyM)
    }

    @Test
    fun theFixAgeCountsFromWhenTheFixArrived() {
        val a = phone("A", seed = 1, battery = 50)
        val c = phone("C", seed = 3, battery = 100, role = Role.RESCUER)
        a.sendSos(details, fix = null)
        radio.run(5 * 60_000)

        a.setFix(fix)
        radio.run(2 * 60_000 + 3_000)

        assertEquals(2, c.knownSos.value.single().fixAgeMin)
    }

    @Test
    fun withoutAnSosAFixIsNotSent() {
        val a = phone("A", seed = 1, battery = 50)
        a.setFix(fix)
        assertNull(a.mySos.value)
        a.sendSos(details, fix = null)
        assertNull(a.mySos.value!!.record.latitude)
    }

    @Test
    fun aPhoneThatTakesOverCarriesTheLatestPosition() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        a.sendSos(details, fix = null)
        radio.run(3_000)
        a.setFix(fix)
        radio.run(3_000)

        radio.switchOff("A")
        radio.run(17_000)

        assertEquals(51.5, b.carried.value.single().latitude!!, 1e-6)
    }
}
