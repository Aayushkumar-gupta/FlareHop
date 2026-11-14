package com.flarehop.flare

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class RealFlareEngineTest {
    private var now = 1_000_000L
    private val engine = RealFlareEngine(clock = { now }, random = Random(7))

    private val details = SosDetails(
        people = 2,
        injured = true,
        trapped = true,
        water = false,
        medical = false,
        fire = false,
    )

    private val fix = Fix(latitude = 11.2588, longitude = 75.7804, accuracyM = 12, fixAgeMin = 1)

    private fun heardSos(messageId: Long = 42L, carrierId: Int = 500, battery: Int? = 60) =
        PacketCodec.encode(
            SosPacket(
                messageId = messageId,
                originId = 500,
                carrierId = carrierId,
                sound = SoundType.NONE,
                soundAgeMin = null,
                latitude = 11.0,
                longitude = 75.0,
                accuracyM = 10,
                fixAgeMin = 2,
                details = details,
                battery = battery,
            ),
        )

    private fun receiptFor(messageId: Long) = PacketCodec.encode(
        ReceiptPacket(receiptId = 9L, rescuerId = 900, ackedMessageId = messageId),
    )

    private fun cancelFor(messageId: Long) = PacketCodec.encode(
        HandoffPacket(
            messageId = messageId,
            originId = 500,
            carrierId = 500,
            successorId = 0,
            battery = 5,
        ),
    )

    private fun next(): Packet? =
        engine.nextPacket()?.let { (PacketCodec.decode(it) as Decoded.Ok).packet }

    private fun TestScope.collectLog(): MutableList<LogEvent> {
        val events = mutableListOf<LogEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.log.toList(events)
        }
        return events
    }

    @Test
    fun phoneIdIsNeverZero() {
        assertTrue(engine.phoneId in 1..65_535)
    }

    @Test
    fun sendSosStartsSendingFromThisPhone() {
        engine.setBattery(47)
        engine.sendSos(details, fix)
        val mine = engine.mySos.value!!
        assertEquals(SosStatus.SENDING, mine.status)
        assertEquals(engine.phoneId, mine.record.originId)
        assertEquals(engine.phoneId, mine.record.carrierId)
        assertEquals(47, mine.record.battery)
        assertTrue(mine.record.messageId in 1L..0xFFFFFFFFL)
    }

    @Test
    fun advertisedSosCarriesTheDetailsAndFix() {
        engine.setBattery(47)
        engine.sendSos(details, fix)
        val packet = next() as SosPacket
        assertEquals(engine.mySos.value!!.record.messageId, packet.messageId)
        assertEquals(details, packet.details)
        assertEquals(11.2588, packet.latitude!!, 0.0)
        assertEquals(47, packet.battery)
    }

    @Test
    fun noFixIsAdvertisedAsNoFix() {
        engine.sendSos(details, null)
        val packet = next() as SosPacket
        assertNull(packet.latitude)
        assertNull(packet.fixAgeMin)
    }

    @Test
    fun fixAgeKeepsCountingUp() {
        engine.sendSos(details, fix)
        now += 3 * 60_000L
        assertEquals(4, (next() as SosPacket).fixAgeMin)
    }

    @Test
    fun latestSoundGoesIntoTheSos() {
        engine.sendSos(details, fix)
        engine.setSound(SoundType.KNOCK, now)
        now += 2 * 60_000L
        val packet = next() as SosPacket
        assertEquals(SoundType.KNOCK, packet.sound)
        assertEquals(2, packet.soundAgeMin)
    }

    @Test
    fun nothingToAdvertiseReturnsNull() {
        assertNull(engine.nextPacket())
    }

    @Test
    fun heardSosCreatesARecord() = runTest {
        val log = collectLog()
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        val record = engine.knownSos.value.single()
        assertEquals(42L, record.messageId)
        assertEquals(500, record.carrierId)
        assertEquals(-60, record.lastRssi)
        assertEquals(now, record.firstHeardAt)
        assertEquals(listOf(LogType.HEARD), log.map { it.type })
    }

    @Test
    fun laterCopiesUpdateTheSameRecord() = runTest {
        val log = collectLog()
        val first = now
        engine.onPacket(heardSos(battery = 60), rssi = -60, atMs = now)
        now += 1_000L
        engine.onPacket(heardSos(battery = 59), rssi = -55, atMs = now)
        val record = engine.knownSos.value.single()
        assertEquals(first, record.firstHeardAt)
        assertEquals(now, record.lastHeardAt)
        assertEquals(-55, record.lastRssi)
        assertEquals(59, record.battery)
        assertEquals(1, log.count { it.type == LogType.HEARD })
    }

    @Test
    fun everyCopyEmitsASignalReading() = runTest {
        val readings = mutableListOf<DirectSignal>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.directSignal.toList(readings)
        }
        engine.onPacket(heardSos(), rssi = -70, atMs = now)
        engine.onPacket(heardSos(), rssi = -65, atMs = now + 300)
        assertEquals(listOf(-70, -65), readings.map { it.rssi })
        assertEquals(42L, readings.first().messageId)
    }

    @Test
    fun badPacketIsDroppedWithAReason() = runTest {
        val log = collectLog()
        engine.onPacket(ByteArray(10), rssi = -60, atMs = now)
        assertTrue(engine.knownSos.value.isEmpty())
        assertEquals(LogType.DROPPED, log.single().type)
        assertEquals("length 10", log.single().reason)
    }

    @Test
    fun rescuerAdvertisesOneReceiptPerSos() = runTest {
        val log = collectLog()
        engine.setRole(Role.RESCUER)
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        val receipt = next() as ReceiptPacket
        assertEquals(42L, receipt.ackedMessageId)
        assertEquals(engine.phoneId, receipt.rescuerId)
        assertEquals(1, log.count { it.type == LogType.RECEIPT_SENT })
    }

    @Test
    fun victimDoesNotSendReceipts() {
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        assertNull(engine.nextPacket())
    }

    @Test
    fun receiptStopsWhenTheSosGoesQuiet() {
        engine.setRole(Role.RESCUER)
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        now += 16_000L
        assertNull(engine.nextPacket())
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        assertTrue(next() is ReceiptPacket)
    }

    @Test
    fun rotationTakesTurnsBetweenPackets() {
        engine.setRole(Role.RESCUER)
        engine.onPacket(heardSos(messageId = 1L), rssi = -60, atMs = now)
        engine.onPacket(heardSos(messageId = 2L), rssi = -60, atMs = now)
        val acked = List(4) { (next() as ReceiptPacket).ackedMessageId }
        assertEquals(listOf(1L, 2L, 1L, 2L), acked)
    }

    @Test
    fun receiptMarksMySosAcked() = runTest {
        val log = collectLog()
        engine.sendSos(details, fix)
        val messageId = engine.mySos.value!!.record.messageId
        engine.onPacket(receiptFor(messageId), rssi = -60, atMs = now)
        engine.onPacket(receiptFor(messageId), rssi = -60, atMs = now)
        assertTrue(engine.mySos.value!!.record.acked)
        assertEquals(1, log.count { it.type == LogType.RECEIPT_MATCHED })
    }

    @Test
    fun receiptMarksAHeardSosAcked() {
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        engine.onPacket(receiptFor(42L), rssi = -60, atMs = now)
        assertTrue(engine.knownSos.value.single().acked)
    }

    @Test
    fun receiptForAnotherSosChangesNothing() {
        engine.sendSos(details, fix)
        engine.onPacket(receiptFor(12345L), rssi = -60, atMs = now)
        assertFalse(engine.mySos.value!!.record.acked)
    }

    @Test
    fun cancelHeardMarksTheRecordCancelled() = runTest {
        val log = collectLog()
        engine.setRole(Role.RESCUER)
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        engine.onPacket(cancelFor(42L), rssi = -60, atMs = now)
        assertTrue(engine.knownSos.value.single().cancelled)
        assertEquals(1, log.count { it.type == LogType.CANCELLED })
        assertNull(engine.nextPacket())
    }

    @Test
    fun cancelSosAdvertisesCancelForTenSeconds() {
        engine.sendSos(details, fix)
        val messageId = engine.mySos.value!!.record.messageId
        engine.cancelSos()
        assertNull(engine.mySos.value)
        val cancel = next() as HandoffPacket
        assertEquals(messageId, cancel.messageId)
        assertEquals(0, cancel.successorId)
        now += 10_000L
        assertNull(engine.nextPacket())
    }

    @Test
    fun resetClearsEverythingAndPicksANewPhoneId() {
        val oldId = engine.phoneId
        engine.setRole(Role.RESCUER)
        engine.sendSos(details, fix)
        engine.onPacket(heardSos(), rssi = -60, atMs = now)
        engine.reset()
        assertNotEquals(oldId, engine.phoneId)
        assertNull(engine.mySos.value)
        assertTrue(engine.knownSos.value.isEmpty())
        assertNull(engine.nextPacket())
    }
}
