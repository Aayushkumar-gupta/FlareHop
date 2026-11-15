package com.flarehop.ui

import com.flarehop.flare.Decoded
import com.flarehop.flare.Fix
import com.flarehop.flare.HandoffPacket
import com.flarehop.flare.Packet
import com.flarehop.flare.PacketCodec
import com.flarehop.flare.ReceiptPacket
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosPacket
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import com.flarehop.flare.StandbyPacket
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MeshResult { NEW, REFRESHED, IGNORED, DROPPED }

// Every phone keeps a copy of every SOS it hears and broadcasts those copies again, so an SOS
// keeps moving from phone to phone after the victim's own phone dies.
class Mesh(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val lock = Any()

    var phoneId: Int = newPhoneId()
        private set

    private var battery: Int? = null
    private var mine: SosRecord? = null
    private val held = LinkedHashMap<Long, SosRecord>()

    // Message IDs that were cancelled or replaced. They are never stored again, so an old copy
    // still bouncing around the mesh cannot bring them back.
    private val retired = HashSet<Long>()
    private val cancels = LinkedHashMap<Long, Relay>()
    private val receipts = LinkedHashMap<Long, Relay>()
    private val seenReceipts = HashSet<Long>()
    private var rotation = 0

    private val _mySos = MutableStateFlow<SosRecord?>(null)
    val mySos: StateFlow<SosRecord?> = _mySos.asStateFlow()

    private val _heldSos = MutableStateFlow<List<SosRecord>>(emptyList())
    val heldSos: StateFlow<List<SosRecord>> = _heldSos.asStateFlow()

    fun send(details: SosDetails, fix: Fix?): SosRecord = synchronized(lock) {
        val now = clock()
        mine?.let { retire(it, now) }
        val record = SosRecord(
            messageId = newMessageId(),
            originId = phoneId,
            carrierId = phoneId,
            latitude = fix?.latitude,
            longitude = fix?.longitude,
            accuracyM = fix?.accuracyM,
            fixAgeMin = fix?.fixAgeMin,
            people = details.people,
            injured = details.injured,
            trapped = details.trapped,
            water = details.water,
            medical = details.medical,
            fire = details.fire,
            battery = battery,
            sound = SoundType.NONE,
            soundAgeMin = null,
            firstHeardAt = now,
            lastHeardAt = now,
            lastRssi = null,
            acked = false,
            cancelled = false,
        )
        mine = record
        _mySos.value = record
        record
    }

    fun cancel() {
        synchronized(lock) {
            val current = mine ?: return
            retire(current, clock())
            mine = null
            _mySos.value = null
        }
    }

    fun setBattery(percent: Int) {
        synchronized(lock) { battery = percent.coerceIn(0, 100) }
    }

    fun onPacket(bytes: ByteArray, rssi: Int, atMs: Long): MeshResult = synchronized(lock) {
        when (val decoded = PacketCodec.decode(bytes)) {
            is Decoded.Dropped -> MeshResult.DROPPED

            is Decoded.Ok -> when (val packet = decoded.packet) {
                is SosPacket -> onSos(packet, rssi, atMs)
                is ReceiptPacket -> onReceipt(packet, atMs)
                is HandoffPacket -> onHandoff(packet, atMs)
                is StandbyPacket -> MeshResult.IGNORED
            }
        }
    }

    // This phone's own SOS takes every other slot, so relaying many others never drowns it out.
    fun nextPacket(): ByteArray? = synchronized(lock) {
        val now = clock()
        expire(now)
        val relayed = buildList<Packet> {
            held.values
                .filter { !it.cancelled }
                .sortedByDescending { it.lastHeardAt }
                .take(MAX_RELAYED)
                .forEach { add(sosPacket(it, it.lastHeardAt, now)) }
            cancels.values.forEach { add(it.packet) }
            receipts.values.forEach { add(it.packet) }
        }
        val own = mine?.let { sosPacket(it, it.firstHeardAt, now) }
        val slot = rotation++
        val packet = when {
            own != null && (relayed.isEmpty() || slot % 2 == 0) -> own
            relayed.isNotEmpty() -> relayed[(slot / 2) % relayed.size]
            else -> return@synchronized null
        }
        PacketCodec.encode(packet)
    }

    fun reset() {
        synchronized(lock) {
            phoneId = newPhoneId()
            mine = null
            held.clear()
            retired.clear()
            cancels.clear()
            receipts.clear()
            seenReceipts.clear()
            rotation = 0
            _mySos.value = null
            _heldSos.value = emptyList()
        }
    }

    private fun onSos(packet: SosPacket, rssi: Int, atMs: Long): MeshResult {
        // A relay can send this phone's own SOS back to it.
        if (packet.originId == phoneId) return MeshResult.IGNORED
        if (packet.messageId in retired) return MeshResult.IGNORED
        val existing = held[packet.messageId]
        // The victim's phone only ever broadcasts its current SOS, so a new message ID heard
        // straight from it means the old one was replaced (Contract 2). Relayed copies cannot
        // tell which is newer, so both are kept and the dashboard shows the newest per origin.
        if (existing == null && packet.carrierId == packet.originId) {
            held.values
                .filter { it.originId == packet.originId }
                .forEach { retire(it, atMs) }
        }
        held[packet.messageId] = SosRecord(
            messageId = packet.messageId,
            originId = packet.originId,
            carrierId = packet.carrierId,
            latitude = packet.latitude,
            longitude = packet.longitude,
            accuracyM = packet.accuracyM,
            fixAgeMin = packet.fixAgeMin,
            people = packet.details.people,
            injured = packet.details.injured,
            trapped = packet.details.trapped,
            water = packet.details.water,
            medical = packet.details.medical,
            fire = packet.details.fire,
            battery = packet.battery,
            sound = packet.sound,
            soundAgeMin = packet.soundAgeMin,
            firstHeardAt = existing?.firstHeardAt ?: atMs,
            lastHeardAt = atMs,
            lastRssi = rssi,
            acked = existing?.acked ?: false,
            cancelled = false,
        )
        trim()
        publish()
        return if (existing == null) MeshResult.NEW else MeshResult.REFRESHED
    }

    // Receipts travel through the mesh too, so a victim out of the rescuer's range still
    // learns that help is coming.
    private fun onReceipt(packet: ReceiptPacket, atMs: Long): MeshResult {
        if (!seenReceipts.add(packet.receiptId)) return MeshResult.REFRESHED
        receipts[packet.receiptId] = Relay(packet, atMs + RECEIPT_RELAY_MS)
        held[packet.ackedMessageId]?.let { held[it.messageId] = it.copy(acked = true) }
        mine?.takeIf { it.messageId == packet.ackedMessageId }?.let {
            mine = it.copy(acked = true)
            _mySos.value = mine
        }
        publish()
        return MeshResult.NEW
    }

    // Only successor 0 (cancel) matters here; every phone in the mesh already carries a copy,
    // so a handoff to one named phone is not needed.
    private fun onHandoff(packet: HandoffPacket, atMs: Long): MeshResult {
        if (packet.successorId != 0) return MeshResult.IGNORED
        if (packet.messageId in retired) return MeshResult.REFRESHED
        retired += packet.messageId
        held[packet.messageId]?.let { held[it.messageId] = it.copy(cancelled = true) }
        cancels[packet.messageId] = Relay(packet.copy(carrierId = phoneId), atMs + CANCEL_RELAY_MS)
        publish()
        return MeshResult.NEW
    }

    // Tells the rest of the mesh to stop carrying an SOS, for a limited time so the cancel
    // itself does not circle forever.
    private fun retire(record: SosRecord, now: Long) {
        retired += record.messageId
        held[record.messageId]?.let { held[it.messageId] = it.copy(cancelled = true) }
        cancels[record.messageId] = Relay(
            HandoffPacket(
                messageId = record.messageId,
                originId = record.originId,
                carrierId = phoneId,
                successorId = 0,
                battery = battery,
            ),
            now + CANCEL_RELAY_MS,
        )
        publish()
    }

    // Ages were measured when the copy arrived, so the minutes since then are added, and this
    // phone signs the copy as its carrier.
    private fun sosPacket(record: SosRecord, agesFromMs: Long, now: Long): SosPacket {
        val elapsed = minutesBetween(agesFromMs, now)
        return SosPacket(
            messageId = record.messageId,
            originId = record.originId,
            carrierId = phoneId,
            sound = record.sound,
            soundAgeMin = record.soundAgeMin?.plus(elapsed),
            latitude = record.latitude,
            longitude = record.longitude,
            accuracyM = record.accuracyM,
            fixAgeMin = record.fixAgeMin?.plus(elapsed),
            details = SosDetails(
                people = record.people,
                injured = record.injured,
                trapped = record.trapped,
                water = record.water,
                medical = record.medical,
                fire = record.fire,
            ),
            battery = battery,
        )
    }

    // Phones keep refreshing each other's copies, so an SOS only expires once no phone in
    // range has carried it for a long time.
    private fun expire(now: Long) {
        val before = held.size
        held.values.removeAll { now - it.lastHeardAt > KEEP_MS }
        cancels.values.removeAll { now >= it.untilMs }
        receipts.values.removeAll { now >= it.untilMs }
        if (held.size != before) publish()
    }

    private fun trim() {
        while (held.size > MAX_HELD) {
            val oldest = held.values.minBy { it.lastHeardAt }
            held.remove(oldest.messageId)
        }
    }

    private fun publish() {
        _heldSos.value = held.values.toList()
    }

    private fun minutesBetween(fromMs: Long, toMs: Long): Int =
        ((toMs - fromMs).coerceAtLeast(0L) / MS_PER_MIN).toInt()

    private fun newPhoneId(): Int = random.nextInt(1, 0x1_0000)

    private fun newMessageId(): Long = random.nextLong(1L, 0x1_0000_0000L)

    private class Relay(val packet: Packet, val untilMs: Long)

    private companion object {
        const val MAX_RELAYED = 16
        const val MAX_HELD = 64
        const val KEEP_MS = 60 * 60_000L
        const val CANCEL_RELAY_MS = 60_000L
        const val RECEIPT_RELAY_MS = 60_000L
        const val MS_PER_MIN = 60_000L
    }
}
