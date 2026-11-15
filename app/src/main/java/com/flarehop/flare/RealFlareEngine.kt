package com.flarehop.flare

import com.flarehop.flare.FlareConstants.CANCEL_ADVERT_MS
import com.flarehop.flare.FlareConstants.HANDOFF_BATTERY_PERCENT
import com.flarehop.flare.FlareConstants.HANDOFF_RETRY_MS
import com.flarehop.flare.FlareConstants.IN_RANGE_MS
import com.flarehop.flare.FlareConstants.JOIN_RSSI_DBM
import com.flarehop.flare.FlareConstants.RECEIPT_KEEP_MS
import com.flarehop.flare.FlareConstants.STANDBY_INTERVAL_MS
import com.flarehop.flare.FlareConstants.TAKEOVER_SILENCE_MS
import com.flarehop.flare.FlareConstants.TAKEOVER_STEP_MS
import kotlin.random.Random
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class RealFlareEngine(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : FlareEngine {
    private val lock = Any()

    private val mySosState = MutableStateFlow<MySos?>(null)
    private val knownSosState = MutableStateFlow<List<SosRecord>>(emptyList())
    private val carriedState = MutableStateFlow<List<SosRecord>>(emptyList())
    private val groupState = MutableStateFlow<List<GroupMember>>(emptyList())
    private val signals = MutableSharedFlow<DirectSignal>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val events = MutableSharedFlow<LogEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val mySos: StateFlow<MySos?> = mySosState.asStateFlow()
    override val knownSos: StateFlow<List<SosRecord>> = knownSosState.asStateFlow()
    override val carried: StateFlow<List<SosRecord>> = carriedState.asStateFlow()
    override val group: StateFlow<List<GroupMember>> = groupState.asStateFlow()
    override val directSignal: SharedFlow<DirectSignal> = signals.asSharedFlow()
    override val log: SharedFlow<LogEvent> = events.asSharedFlow()

    var phoneId: Int = newPhoneId()
        private set

    private var role = Role.SURVIVOR
    private var battery: Int? = null
    private var sound = SoundType.NONE
    private var soundAtMs = 0L

    // When this phone's own position was last set; the fix age in each SOS counts from here.
    private var fixAtMs = 0L
    private val records = LinkedHashMap<Long, SosRecord>()
    private val receipts = LinkedHashMap<Long, ReceiptPacket>()
    private var cancel: HandoffPacket? = null
    private var cancelUntilMs = 0L
    private var rotation = 0
    private val neighbours = LinkedHashMap<Int, Neighbour>()
    private var lastStandbyMs: Long? = null
    private val carriedRecords = LinkedHashMap<Long, SosRecord>()
    private val handoffs = HashMap<Long, Handoff>()

    private class Handoff(val successorId: Int, val namedAt: Long, val tried: Set<Int>)

    private class Neighbour(var battery: Int?, var rssi: Int, var lastHeardAt: Long) {
        val carrying = HashMap<Long, Long>()
    }

    override fun onPacket(bytes: ByteArray, rssi: Int, atMs: Long) {
        synchronized(lock) {
            when (val decoded = PacketCodec.decode(bytes)) {
                is Decoded.Dropped -> emit(LogType.DROPPED, 0L, 0, atMs, decoded.reason)

                is Decoded.Ok -> when (val packet = decoded.packet) {
                    is SosPacket -> onSos(packet, rssi, atMs)

                    is ReceiptPacket -> onReceipt(packet, atMs)

                    is HandoffPacket ->
                        if (packet.successorId ==
                            0
                        ) {
                            onCancel(packet, atMs)
                        } else {
                            onHandoff(packet, atMs)
                        }

                    is StandbyPacket -> onStandby(packet, rssi, atMs)
                }
            }
        }
    }

    override fun sendSos(details: SosDetails, fix: Fix?) {
        synchronized(lock) {
            val now = clock()
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
                sound = sound,
                soundAgeSec = soundAgeSec(now),
                firstHeardAt = now,
                lastHeardAt = now,
                lastRssi = null,
                acked = false,
                cancelled = false,
            )
            fixAtMs = now
            cancel = null
            mySosState.value = MySos(record, SosStatus.SENDING)
        }
    }

    override fun cancelSos() {
        synchronized(lock) {
            val current = mySosState.value ?: return
            cancel = HandoffPacket(
                messageId = current.record.messageId,
                originId = current.record.originId,
                carrierId = phoneId,
                successorId = 0,
                battery = battery,
            )
            cancelUntilMs = clock() + CANCEL_ADVERT_MS
            handoffs.remove(current.record.messageId)
            mySosState.value = null
        }
    }

    override fun setRole(role: Role) {
        synchronized(lock) { this.role = role }
    }

    override fun setBattery(percent: Int) {
        synchronized(lock) {
            battery = percent.coerceIn(0, 100)
            updateMyRecord { it.copy(battery = battery) }
        }
    }

    // GPS often has no fix yet when SOS is tapped, so a fix that arrives later still reaches
    // every phone, under the same SOS.
    override fun setFix(fix: Fix) {
        synchronized(lock) {
            if (mySosState.value == null) return
            fixAtMs = clock()
            updateMyRecord {
                it.copy(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracyM = fix.accuracyM,
                    fixAgeMin = fix.fixAgeMin,
                )
            }
        }
    }

    override fun setSound(type: SoundType, atMs: Long) {
        synchronized(lock) {
            sound = type
            soundAtMs = atMs
            updateMyRecord { it.copy(sound = type, soundAgeSec = soundAgeSec(clock())) }
        }
    }

    override fun nextPacket(): ByteArray? {
        synchronized(lock) {
            val now = clock()
            if (cancel != null && now >= cancelUntilMs) cancel = null
            publishGroup(now)
            planHandoffs(now)
            planTakeovers(now)
            val packets = buildList {
                mySosState.value
                    ?.takeIf { it.status == SosStatus.SENDING }
                    ?.let { add(sosPacket(it.record, now)) }
                carriedRecords.values.forEach { add(carriedPacket(it, now)) }
                handoffs.forEach { (messageId, handoff) ->
                    handoffPacket(messageId, handoff.successorId)?.let { add(it) }
                }
                cancel?.let { add(it) }
                receipts.forEach { (messageId, receipt) ->
                    val heard = records[messageId]?.lastHeardAt ?: return@forEach
                    if (now - heard <= RECEIPT_KEEP_MS) add(receipt)
                }
            }
            standbyDue(now, nothingElse = packets.isEmpty())?.let { return PacketCodec.encode(it) }
            if (packets.isEmpty()) return null
            val packet = packets[rotation % packets.size]
            rotation++
            return PacketCodec.encode(packet)
        }
    }

    override fun reset() {
        synchronized(lock) {
            phoneId = newPhoneId()
            sound = SoundType.NONE
            soundAtMs = 0L
            fixAtMs = 0L
            records.clear()
            receipts.clear()
            neighbours.clear()
            lastStandbyMs = null
            carriedRecords.clear()
            handoffs.clear()
            cancel = null
            cancelUntilMs = 0L
            rotation = 0
            mySosState.value = null
            knownSosState.value = emptyList()
            carriedState.value = emptyList()
            groupState.value = emptyList()
        }
    }

    private fun onSos(packet: SosPacket, rssi: Int, atMs: Long) {
        confirmHandoff(packet.messageId, packet.carrierId)
        if (packet.messageId == mySosState.value?.record?.messageId) {
            hearNeighbour(packet.carrierId, packet.battery, rssi, atMs, packet.messageId)
            return
        }
        val existing = records[packet.messageId]
        records[packet.messageId] = SosRecord(
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
            soundAgeSec = packet.soundAgeSec,
            firstHeardAt = existing?.firstHeardAt ?: atMs,
            lastHeardAt = atMs,
            lastRssi = rssi,
            acked = existing?.acked ?: false,
            cancelled = existing?.cancelled ?: false,
        )
        publishRecords()
        if (existing == null) emit(LogType.HEARD, packet.messageId, packet.carrierId, atMs)
        hearNeighbour(packet.carrierId, packet.battery, rssi, atMs, packet.messageId)
        resolveConflict(packet, atMs)
        signals.tryEmit(DirectSignal(packet.messageId, packet.carrierId, rssi, atMs))
        if (role == Role.RESCUER && packet.messageId !in receipts) {
            receipts[packet.messageId] = ReceiptPacket(
                receiptId = newMessageId(),
                rescuerId = phoneId,
                ackedMessageId = packet.messageId,
            )
            emit(LogType.RECEIPT_SENT, packet.messageId, phoneId, atMs)
        }
    }

    private fun standbyDue(now: Long, nothingElse: Boolean): StandbyPacket? {
        val mine = mySosState.value?.record
        val inMesh = role == Role.SURVIVOR &&
            (mine != null || carriedRecords.isNotEmpty() || records.values.any { !it.cancelled })
        if (!inMesh) {
            lastStandbyMs = null
            return null
        }
        val last = lastStandbyMs
        val due = nothingElse || (last != null && now - last >= STANDBY_INTERVAL_MS)
        if (last == null) lastStandbyMs = now
        if (!due) return null
        lastStandbyMs = now
        return StandbyPacket(
            messageId = mine?.messageId ?: 0L,
            originId = mine?.originId ?: 0,
            standbyId = phoneId,
            battery = battery,
        )
    }

    private fun onStandby(packet: StandbyPacket, rssi: Int, atMs: Long) {
        hearNeighbour(packet.standbyId, packet.battery, rssi, atMs, carriedMessageId = null)
    }

    private fun hearNeighbour(
        id: Int,
        battery: Int?,
        rssi: Int,
        atMs: Long,
        carriedMessageId: Long?,
    ) {
        if (id == phoneId || rssi < JOIN_RSSI_DBM) return
        val neighbour = neighbours[id]
        if (neighbour == null) {
            neighbours[id] = Neighbour(battery, rssi, atMs)
            emit(LogType.JOINED, carriedMessageId ?: 0L, id, atMs)
        } else {
            neighbour.battery = battery
            neighbour.rssi = rssi
            neighbour.lastHeardAt = atMs
        }
        if (carriedMessageId != null) neighbours.getValue(id).carrying[carriedMessageId] = atMs
        publishGroup(atMs)
    }

    private fun publishGroup(now: Long) {
        neighbours.values.removeAll { now - it.lastHeardAt > IN_RANGE_MS }
        val members = neighbours.map { (id, neighbour) ->
            neighbour.carrying.values.removeAll { now - it > IN_RANGE_MS }
            GroupMember(
                phoneId = id,
                battery = neighbour.battery,
                rssi = neighbour.rssi,
                lastHeardAt = neighbour.lastHeardAt,
                carriedCount = neighbour.carrying.size,
            )
        }
        if (members != groupState.value) groupState.value = members
    }

    private fun onReceipt(packet: ReceiptPacket, atMs: Long) {
        var matched = false
        records[packet.ackedMessageId]?.let { record ->
            if (!record.acked) {
                records[packet.ackedMessageId] = record.copy(acked = true)
                publishRecords()
                matched = true
            }
        }
        mySosState.value?.let { mine ->
            if (mine.record.messageId == packet.ackedMessageId && !mine.record.acked) {
                mySosState.value = mine.copy(record = mine.record.copy(acked = true))
                matched = true
            }
        }
        if (matched) emit(LogType.RECEIPT_MATCHED, packet.ackedMessageId, packet.rescuerId, atMs)
    }

    private fun onHandoff(packet: HandoffPacket, atMs: Long) {
        if (packet.successorId != phoneId || role != Role.SURVIVOR) return
        if (packet.messageId in carriedRecords) return
        val copy = records[packet.messageId] ?: return
        carriedRecords[packet.messageId] = copy.copy(carrierId = phoneId)
        publishCarried()
        emit(LogType.TOOK_OVER, packet.messageId, phoneId, atMs)
    }

    private fun confirmHandoff(messageId: Long, carrierId: Int) {
        if (handoffs[messageId]?.successorId != carrierId) return
        handoffs.remove(messageId)
        val mine = mySosState.value
        if (mine != null && mine.record.messageId == messageId) {
            mySosState.value = MySos(mine.record.copy(carrierId = carrierId), SosStatus.HANDED_OFF)
        }
        if (carriedRecords.remove(messageId) != null) publishCarried()
    }

    // Contract 3 rule 8: another phone broadcasts an SOS this phone carries. The victim's own phone
    // always keeps it; otherwise less battery stops, and on a tie the higher phone ID. Both sides
    // apply the same test, so exactly one stops. The copy stays in records for a later takeover.
    private fun resolveConflict(packet: SosPacket, atMs: Long) {
        if (packet.messageId !in carriedRecords) return
        val theirs = packet.battery ?: -1
        val mine = battery ?: -1
        val stop = packet.carrierId == packet.originId ||
            theirs > mine ||
            (theirs == mine && phoneId > packet.carrierId)
        if (!stop) return
        carriedRecords.remove(packet.messageId)
        handoffs.remove(packet.messageId)
        publishCarried()
        emit(LogType.BACKED_OFF, packet.messageId, packet.carrierId, atMs)
    }

    private fun planHandoffs(now: Long) {
        val level = battery
        if (level == null || level >= HANDOFF_BATTERY_PERCENT) {
            handoffs.clear()
            return
        }
        val broadcasting = buildList {
            mySosState.value
                ?.takeIf { it.status == SosStatus.SENDING }
                ?.let { add(it.record.messageId) }
            addAll(carriedRecords.keys)
        }
        handoffs.keys.retainAll(broadcasting.toSet())
        broadcasting.forEach { messageId ->
            val pending = handoffs[messageId]
            if (pending != null && now - pending.namedAt < HANDOFF_RETRY_MS) return@forEach
            val tried = pending?.let { it.tried + it.successorId } ?: emptySet()
            val next = strongestInRange(level, exclude = tried)
                ?: strongestInRange(level, exclude = emptySet())
                ?: return@forEach
            val stillTried = if (next.phoneId in tried) emptySet() else tried
            handoffs[messageId] = Handoff(next.phoneId, now, stillTried)
            emit(LogType.HANDOFF_SENT, messageId, next.phoneId, now)
        }
    }

    // Contract 3 rule 7: a copy whose in-range carrier went silent is taken over. Each phone with
    // more battery in range adds a step to the wait, so the strongest acts first and the others
    // hear it broadcast again before their own wait runs out.
    private fun planTakeovers(now: Long) {
        if (role != Role.SURVIVOR) return
        val level = battery ?: return
        if (level < HANDOFF_BATTERY_PERCENT) return
        val mine = mySosState.value?.record?.messageId
        var changed = false
        records.values.forEach { record ->
            if (record.cancelled || record.messageId == mine) return@forEach
            if (record.messageId in carriedRecords) return@forEach
            val rssi = record.lastRssi ?: return@forEach
            if (rssi < JOIN_RSSI_DBM) return@forEach
            val stronger = groupState.value.count { member ->
                val other = member.battery
                other != null && other > level
            }
            val wait = TAKEOVER_SILENCE_MS + TAKEOVER_STEP_MS * stronger
            if (now - record.lastHeardAt < wait) return@forEach
            carriedRecords[record.messageId] = record.copy(carrierId = phoneId)
            emit(LogType.TOOK_OVER, record.messageId, phoneId, now)
            changed = true
        }
        if (changed) publishCarried()
    }

    private fun strongestInRange(myLevel: Int, exclude: Set<Int>): GroupMember? = groupState.value
        .filter { member ->
            val level = member.battery
            level != null && level > myLevel && member.phoneId !in exclude
        }
        .maxWithOrNull(
            compareBy<GroupMember> { it.battery }.thenByDescending { it.phoneId },
        )

    private fun handoffPacket(messageId: Long, successorId: Int): HandoffPacket? {
        val record = carriedRecords[messageId]
            ?: mySosState.value?.record?.takeIf { it.messageId == messageId }
            ?: return null
        return HandoffPacket(
            messageId = messageId,
            originId = record.originId,
            carrierId = phoneId,
            successorId = successorId,
            battery = battery,
        )
    }

    private fun onCancel(packet: HandoffPacket, atMs: Long) {
        receipts.remove(packet.messageId)
        handoffs.remove(packet.messageId)
        if (carriedRecords.remove(packet.messageId) != null) publishCarried()
        val record = records[packet.messageId] ?: return
        if (record.cancelled) return
        records[packet.messageId] = record.copy(cancelled = true)
        publishRecords()
        emit(LogType.CANCELLED, packet.messageId, packet.carrierId, atMs)
    }

    private fun sosPacket(record: SosRecord, now: Long) = SosPacket(
        messageId = record.messageId,
        originId = record.originId,
        carrierId = phoneId,
        sound = sound,
        soundAgeSec = soundAgeSec(now),
        latitude = record.latitude,
        longitude = record.longitude,
        accuracyM = record.accuracyM,
        fixAgeMin = record.fixAgeMin?.plus(minutesBetween(fixAtMs, now)),
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

    private fun carriedPacket(record: SosRecord, now: Long): SosPacket {
        val minutes = minutesBetween(record.lastHeardAt, now)
        return SosPacket(
            messageId = record.messageId,
            originId = record.originId,
            carrierId = phoneId,
            sound = record.sound,
            soundAgeSec = record.soundAgeSec?.plus(secondsBetween(record.lastHeardAt, now)),
            latitude = record.latitude,
            longitude = record.longitude,
            accuracyM = record.accuracyM,
            fixAgeMin = record.fixAgeMin?.plus(minutes),
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

    private fun publishCarried() {
        carriedState.value = carriedRecords.values.toList()
    }

    private fun updateMyRecord(change: (SosRecord) -> SosRecord) {
        val mine = mySosState.value ?: return
        mySosState.value = mine.copy(record = change(mine.record))
    }

    private fun publishRecords() {
        knownSosState.value = records.values.toList()
    }

    private fun emit(
        type: LogType,
        messageId: Long,
        phoneId: Int,
        atMs: Long,
        reason: String? = null,
    ) {
        events.tryEmit(LogEvent(type, messageId, phoneId, atMs, reason))
    }

    private fun soundAgeSec(now: Long): Int? =
        if (sound == SoundType.NONE) null else secondsBetween(soundAtMs, now)

    private fun secondsBetween(fromMs: Long, toMs: Long): Int =
        ((toMs - fromMs).coerceAtLeast(0L) / 1_000L).toInt()

    private fun minutesBetween(fromMs: Long, toMs: Long): Int =
        ((toMs - fromMs).coerceAtLeast(0L) / 60_000L).toInt()

    private fun newPhoneId(): Int = random.nextInt(1, 0x1_0000)

    private fun newMessageId(): Long = random.nextLong(1L, 0x1_0000_0000L)
}
