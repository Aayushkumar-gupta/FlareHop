package com.flarehop.flare

import com.flarehop.handoff.FakeRadio
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeshGroupTest {
    private var now = 1_000_000L
    private val engine = RealFlareEngine(clock = { now }, random = Random(11))

    private val details = SosDetails(
        people = 1,
        injured = false,
        trapped = true,
        water = false,
        medical = false,
        fire = false,
    )

    private fun sos(messageId: Long = 42L, carrierId: Int = 500, battery: Int? = 60) =
        PacketCodec.encode(
            SosPacket(
                messageId = messageId,
                originId = carrierId,
                carrierId = carrierId,
                sound = SoundType.NONE,
                soundAgeMin = null,
                latitude = null,
                longitude = null,
                accuracyM = null,
                fixAgeMin = null,
                details = details,
                battery = battery,
            ),
        )

    private fun standby(phoneId: Int, battery: Int?) = PacketCodec.encode(
        StandbyPacket(messageId = 0L, originId = 0, standbyId = phoneId, battery = battery),
    )

    private fun cancel(messageId: Long, carrierId: Int) = PacketCodec.encode(
        HandoffPacket(
            messageId = messageId,
            originId = carrierId,
            carrierId = carrierId,
            successorId = 0,
            battery = 5,
        ),
    )

    private fun next(): Packet? =
        engine.nextPacket()?.let { (PacketCodec.decode(it) as Decoded.Ok).packet }

    @Test
    fun phoneThatHeardNothingStaysQuiet() {
        assertNull(engine.nextPacket())
    }

    @Test
    fun phoneThatHearsAnSosJoinsWithAStandby() {
        engine.setBattery(71)
        engine.onPacket(sos(), rssi = -60, atMs = now)
        val standby = next() as StandbyPacket
        assertEquals(engine.phoneId, standby.standbyId)
        assertEquals(71, standby.battery)
        assertEquals(0L, standby.messageId)
    }

    @Test
    fun phoneSendingItsOwnSosReportsItselfEveryFiveSeconds() {
        engine.setBattery(64)
        engine.sendSos(details, fix = null)
        val mine = engine.mySos.value!!.record
        assertTrue(List(4) { next() }.all { it is SosPacket })
        now += FlareConstants.STANDBY_INTERVAL_MS
        val standby = next() as StandbyPacket
        assertEquals(mine.messageId, standby.messageId)
        assertEquals(mine.originId, standby.originId)
        assertEquals(engine.phoneId, standby.standbyId)
        assertEquals(64, standby.battery)
        assertTrue(next() is SosPacket)
    }

    @Test
    fun rescuerNeverSendsStandby() {
        engine.setRole(Role.RESCUER)
        engine.onPacket(sos(), rssi = -60, atMs = now)
        val packets = List(4) {
            now += FlareConstants.STANDBY_INTERVAL_MS
            next()
        }
        assertFalse(packets.any { it is StandbyPacket })
    }

    @Test
    fun cancelledSosEndsTheStandby() {
        engine.onPacket(sos(), rssi = -60, atMs = now)
        engine.onPacket(cancel(42L, 500), rssi = -60, atMs = now)
        assertNull(engine.nextPacket())
    }

    @Test
    fun closeStandbyJoinsTheGroupWithItsBattery() {
        engine.onPacket(standby(phoneId = 700, battery = 71), rssi = -60, atMs = now)
        val member = engine.group.value.single()
        assertEquals(700, member.phoneId)
        assertEquals(71, member.battery)
        assertEquals(-60, member.rssi)
        assertEquals(0, member.carriedCount)
    }

    @Test
    fun weakSignalStaysOutOfTheGroup() {
        engine.onPacket(standby(phoneId = 700, battery = 71), rssi = -80, atMs = now)
        assertTrue(engine.group.value.isEmpty())
    }

    @Test
    fun sosCarrierCountsEveryMessageItBroadcasts() {
        engine.onPacket(sos(messageId = 1L, carrierId = 500, battery = 40), rssi = -60, atMs = now)
        engine.onPacket(sos(messageId = 2L, carrierId = 500, battery = 40), rssi = -60, atMs = now)
        val member = engine.group.value.single()
        assertEquals(500, member.phoneId)
        assertEquals(40, member.battery)
        assertEquals(2, member.carriedCount)
    }

    @Test
    fun silentPhoneLeavesTheGroupAfter15Seconds() {
        engine.onPacket(standby(phoneId = 700, battery = 71), rssi = -60, atMs = now)
        now += 16_000L
        engine.nextPacket()
        assertTrue(engine.group.value.isEmpty())
    }

    @Test
    fun joiningIsLoggedOnce() = runTest {
        val log = mutableListOf<LogEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.log.toList(log)
        }
        engine.onPacket(standby(phoneId = 700, battery = 71), rssi = -60, atMs = now)
        engine.onPacket(standby(phoneId = 700, battery = 70), rssi = -60, atMs = now + 1_000)
        assertEquals(1, log.count { it.type == LogType.JOINED && it.phoneId == 700 })
    }

    @Test
    fun threePhonesFindEachOther() {
        val radio = FakeRadio()
        fun phone(name: String, seed: Int, battery: Int) =
            radio.add(name, RealFlareEngine(clock = radio.clock, random = Random(seed)))
                .also { it.setBattery(battery) }
        val a = phone("A", seed = 1, battery = 30)
        val b = phone("B", seed = 2, battery = 80)
        val c = phone("C", seed = 3, battery = 60)
        a.sendSos(details, fix = null)
        radio.run(2_000)
        assertEquals(setOf(b.phoneId, c.phoneId), a.group.value.map { it.phoneId }.toSet())
        val aSeenByB = b.group.value.single { it.phoneId == a.phoneId }
        assertEquals(30, aSeenByB.battery)
        assertEquals(1, aSeenByB.carriedCount)
        assertEquals(80, c.group.value.single { it.phoneId == b.phoneId }.battery)
    }
}
