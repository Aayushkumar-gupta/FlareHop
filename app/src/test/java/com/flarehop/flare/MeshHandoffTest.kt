package com.flarehop.flare

import com.flarehop.handoff.FakeRadio
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshHandoffTest {
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

    private fun sendAndSettle(victim: RealFlareEngine) {
        victim.sendSos(details, fix = null)
        radio.run(6_000)
    }

    @Test
    fun lowBatteryHandsTheSosToThePhoneWithTheMostCharge() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 40)
        sendAndSettle(a)
        val sos = a.mySos.value!!.record

        a.setBattery(9)
        radio.run(3_000)

        assertEquals(SosStatus.HANDED_OFF, a.mySos.value!!.status)
        assertEquals(b.phoneId, a.mySos.value!!.record.carrierId)
        val copy = b.carried.value.single()
        assertEquals(sos.messageId, copy.messageId)
        assertEquals(a.phoneId, copy.originId)
        assertEquals(b.phoneId, copy.carrierId)
        assertTrue(d.carried.value.isEmpty())
    }

    @Test
    fun tiesGoToTheLowestPhoneId() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 80)
        sendAndSettle(a)
        a.setBattery(9)
        radio.run(3_000)
        val lower = if (b.phoneId < d.phoneId) b else d
        val higher = if (lower === b) d else b
        assertEquals(1, lower.carried.value.size)
        assertTrue(higher.carried.value.isEmpty())
    }

    @Test
    fun aPhoneWithUnknownBatteryIsNeverChosen() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = null)
        val d = phone("D", seed = 4, battery = 40)
        sendAndSettle(a)
        a.setBattery(9)
        radio.run(3_000)
        assertTrue(b.carried.value.isEmpty())
        assertEquals(1, d.carried.value.size)
    }

    @Test
    fun aWeakSignalIsNeverChosen() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 90)
        val d = phone("D", seed = 4, battery = 40)
        radio.signal("A", "B", dbm = -80)
        sendAndSettle(a)
        a.setBattery(9)
        radio.run(3_000)
        assertTrue(b.carried.value.isEmpty())
        assertEquals(1, d.carried.value.size)
    }

    @Test
    fun aRescuerIsNeverChosen() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 60)
        val c = phone("C", seed = 3, battery = 100, role = Role.RESCUER)
        sendAndSettle(a)
        a.setBattery(9)
        radio.run(3_000)
        assertTrue(c.carried.value.isEmpty())
        assertEquals(1, b.carried.value.size)
    }

    @Test
    fun nobodyWithMoreChargeMeansTheSosStaysPut() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 5)
        sendAndSettle(a)
        a.setBattery(9)
        radio.run(3_000)
        assertEquals(SosStatus.SENDING, a.mySos.value!!.status)
        assertTrue(b.carried.value.isEmpty())
    }

    @Test
    fun theRescuerFollowsTheNewCarrierWithTheVictimsDetails() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val c = phone("C", seed = 3, battery = 100, role = Role.RESCUER)
        sendAndSettle(a)
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
    }

    @Test
    fun aCarrierAboutToDieHandsOnEverythingItCarries() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        val d = phone("D", seed = 4, battery = 60)
        sendAndSettle(a)
        b.sendSos(details, fix = null)
        radio.run(6_000)
        a.setBattery(9)
        radio.run(3_000)
        assertEquals(1, b.carried.value.size)

        b.setBattery(8)
        radio.run(4_000)

        assertTrue(b.carried.value.isEmpty())
        assertEquals(SosStatus.HANDED_OFF, b.mySos.value!!.status)
        val origins = d.carried.value.map { it.originId }.toSet()
        assertEquals(setOf(a.phoneId, b.phoneId), origins)
    }

    @Test
    fun noAnswerWithin30SecondsNamesTheNextPhone() {
        var now = 1_000_000L
        val engine = RealFlareEngine(clock = { now }, random = Random(9))
        fun standby(id: Int, battery: Int) = PacketCodec.encode(
            StandbyPacket(messageId = 0L, originId = 0, standbyId = id, battery = battery),
        )
        fun hearBoth() {
            engine.onPacket(standby(700, 80), rssi = -60, atMs = now)
            engine.onPacket(standby(800, 60), rssi = -60, atMs = now)
        }
        engine.setBattery(50)
        engine.sendSos(details, fix = null)
        hearBoth()
        engine.setBattery(9)
        fun namedSuccessors() = List(4) { engine.nextPacket() }
            .mapNotNull { (PacketCodec.decode(it!!) as Decoded.Ok).packet as? HandoffPacket }
            .map { it.successorId }
            .toSet()
        assertEquals(setOf(700), namedSuccessors())

        now += 20_000L
        hearBoth()
        assertEquals(setOf(700), namedSuccessors())

        now += 11_000L
        hearBoth()
        assertEquals(setOf(800), namedSuccessors())
    }

    @Test
    fun cancelReleasesTheCarriedCopy() {
        val a = phone("A", seed = 1, battery = 50)
        val b = phone("B", seed = 2, battery = 80)
        sendAndSettle(a)
        a.setBattery(9)
        radio.run(3_000)
        assertEquals(1, b.carried.value.size)
        a.cancelSos()
        radio.run(2_000)
        assertTrue(b.carried.value.isEmpty())
        assertNull(a.mySos.value)
    }
}
