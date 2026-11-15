package com.flarehop.ui

import com.flarehop.flare.DirectSignal
import com.flarehop.flare.Fix
import com.flarehop.flare.FlareConstants
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.GroupMember
import com.flarehop.flare.LogEvent
import com.flarehop.flare.LogType
import com.flarehop.flare.MySos
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus
import com.flarehop.flare.SoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

// Pass a main-thread scope such as viewModelScope: state is changed without locks,
// which is only safe while every change runs on one thread.
class FakeEngine(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : FlareEngine {

    private val _mySos = MutableStateFlow<MySos?>(null)
    override val mySos: StateFlow<MySos?> = _mySos.asStateFlow()

    private val _knownSos = MutableStateFlow<List<SosRecord>>(emptyList())
    override val knownSos: StateFlow<List<SosRecord>> = _knownSos.asStateFlow()

    private val _group = MutableStateFlow<List<GroupMember>>(emptyList())
    override val group: StateFlow<List<GroupMember>> = _group.asStateFlow()

    private val _directSignal = MutableSharedFlow<DirectSignal>(extraBufferCapacity = 64)
    override val directSignal: SharedFlow<DirectSignal> = _directSignal.asSharedFlow()

    private val _log = MutableSharedFlow<LogEvent>(extraBufferCapacity = 64)
    override val log: SharedFlow<LogEvent> = _log.asSharedFlow()

    private var role = Role.VICTIM
    private var phoneId = newPhoneId()
    private var battery = 80
    private var sound = SoundType.NONE
    private var soundAtMs: Long? = null
    private val rssiByMessage = mutableMapOf<Long, Double>()
    private lateinit var session: CoroutineScope

    init {
        start()
    }

    override fun onPacket(bytes: ByteArray, rssi: Int, atMs: Long) = Unit

    override fun nextPacket(): ByteArray? = null

    override fun sendSos(details: SosDetails, fix: Fix?) {
        val t = now()
        val record = SosRecord(
            messageId = random.nextLong(1L, 0x1_0000_0000L),
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
            soundAgeMin = soundAgeMin(t),
            firstHeardAt = t,
            lastHeardAt = t,
            lastRssi = null,
            acked = false,
            cancelled = false,
        )
        _mySos.value = MySos(record, SosStatus.SENDING)
        _group.value = listOf(
            GroupMember(phoneId, isCarrier = true, battery = battery, rssi = null, lastHeardAt = t),
            GroupMember(FAKE_STANDBY_ID, isCarrier = false, battery = FAKE_STANDBY_BATTERY, rssi = -62, lastHeardAt = t),
        )
        emitLog(LogType.JOINED, record.messageId, FAKE_STANDBY_ID)
        session.launch {
            delay(RECEIPT_DELAY_MS)
            val mine = _mySos.value ?: return@launch
            if (mine.record.messageId != record.messageId) return@launch
            _mySos.value = mine.copy(record = mine.record.copy(acked = true))
            emitLog(LogType.RECEIPT_MATCHED, record.messageId, FAKE_RESCUER_ID)
        }
        if (battery < FlareConstants.HANDOFF_BATTERY_PERCENT) handOffMine()
    }

    override fun cancelSos() {
        val mine = _mySos.value ?: return
        emitLog(LogType.CANCELLED, mine.record.messageId, phoneId)
        _mySos.value = null
        _group.value = emptyList()
    }

    override fun setRole(role: Role) {
        this.role = role
        if (role != Role.NEARBY && _mySos.value?.status == SosStatus.STANDING_BY) {
            _mySos.value = null
            _group.value = emptyList()
        }
        when (role) {
            Role.NEARBY -> if (_mySos.value == null) standBy()
            Role.RESCUER -> sendReceipts()
            Role.VICTIM -> Unit
        }
    }

    override fun setBattery(percent: Int) {
        battery = percent.coerceIn(0, 100)
        val mine = _mySos.value
        if (mine != null && mine.status == SosStatus.SENDING) {
            _mySos.value = mine.copy(record = mine.record.copy(battery = battery))
        }
        _group.value = _group.value.map { if (it.phoneId == phoneId) it.copy(battery = battery) else it }
        if (battery < FlareConstants.HANDOFF_BATTERY_PERCENT) handOffMine()
    }

    override fun setSound(type: SoundType, atMs: Long) {
        sound = type
        soundAtMs = atMs
        val mine = _mySos.value ?: return
        if (mine.status == SosStatus.SENDING) {
            _mySos.value = mine.copy(record = mine.record.copy(sound = type, soundAgeMin = soundAgeMin(now())))
        }
    }

    override fun reset() {
        session.cancel()
        phoneId = newPhoneId()
        battery = 80
        sound = SoundType.NONE
        soundAtMs = null
        rssiByMessage.clear()
        _mySos.value = null
        _group.value = emptyList()
        _knownSos.value = emptyList()
        start()
    }

    private fun start() {
        session = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        _knownSos.value = sampleRecords(now())
        session.launch {
            while (isActive) {
                delay(SIGNAL_TICK_MS)
                tickSignal()
            }
        }
        session.launch {
            while (isActive) {
                delay(HANDOFF_EVERY_MS)
                fakeHandoff()
            }
        }
    }

    private fun tickSignal() {
        val t = now()
        val readings = mutableListOf<DirectSignal>()
        _knownSos.value = _knownSos.value.map { record ->
            if (record.cancelled) {
                record
            } else {
                val rssi = nextRssi(record.messageId)
                readings += DirectSignal(record.messageId, record.carrierId, rssi, t)
                record.copy(lastHeardAt = t, lastRssi = rssi)
            }
        }
        readings.forEach { _directSignal.tryEmit(it) }
    }

    private fun fakeHandoff() {
        when (_mySos.value?.status) {
            SosStatus.SENDING -> handOffMine()
            SosStatus.STANDING_BY -> takeOverMine()
            SosStatus.HANDED_OFF, null -> Unit
        }
        swapSampleCarrier()
    }

    private fun handOffMine() {
        val mine = _mySos.value ?: return
        if (mine.status != SosStatus.SENDING) return
        val next = _group.value
            .filter { !it.isCarrier }
            .maxWithOrNull(compareBy<GroupMember> { it.battery ?: -1 }.thenByDescending { it.phoneId })
            ?: return
        _mySos.value = MySos(mine.record.copy(carrierId = next.phoneId, battery = next.battery), SosStatus.HANDED_OFF)
        _group.value = _group.value.map { it.copy(isCarrier = it.phoneId == next.phoneId) }
        emitLog(LogType.HANDOFF_SENT, mine.record.messageId, next.phoneId)
    }

    private fun takeOverMine() {
        val mine = _mySos.value ?: return
        val t = now()
        val record = mine.record.copy(
            carrierId = phoneId,
            battery = battery,
            sound = sound,
            soundAgeMin = soundAgeMin(t),
        )
        _mySos.value = MySos(record, SosStatus.SENDING)
        _group.value = _group.value.map { it.copy(isCarrier = it.phoneId == phoneId, lastHeardAt = t) }
        emitLog(LogType.TOOK_OVER, record.messageId, phoneId)
    }

    private fun standBy() {
        val target = _knownSos.value.firstOrNull { it.messageId == STANDBY_SAMPLE_ID } ?: return
        val t = now()
        _mySos.value = MySos(target, SosStatus.STANDING_BY)
        _group.value = listOf(
            GroupMember(target.carrierId, isCarrier = true, battery = target.battery, rssi = target.lastRssi, lastHeardAt = t),
            GroupMember(phoneId, isCarrier = false, battery = battery, rssi = null, lastHeardAt = t),
        )
        emitLog(LogType.JOINED, target.messageId, phoneId)
    }

    private fun swapSampleCarrier() {
        val record = _knownSos.value.firstOrNull { it.messageId == HANDOFF_SAMPLE_ID } ?: return
        val toStandby = record.carrierId == record.originId
        val carrier = if (toStandby) FAKE_STANDBY_ID else record.originId
        val carrierBattery = if (toStandby) FAKE_STANDBY_BATTERY else SAMPLE_A_BATTERY
        _knownSos.value = _knownSos.value.map {
            if (it.messageId == record.messageId) it.copy(carrierId = carrier, battery = carrierBattery) else it
        }
        rssiByMessage.remove(record.messageId)
        emitLog(LogType.TOOK_OVER, record.messageId, carrier)
    }

    private fun sendReceipts() {
        session.launch {
            delay(RECEIPT_DELAY_MS)
            val unacked = _knownSos.value.filter { !it.acked }
            _knownSos.value = _knownSos.value.map { it.copy(acked = true) }
            unacked.forEach { emitLog(LogType.RECEIPT_SENT, it.messageId, phoneId) }
        }
    }

    private fun nextRssi(messageId: Long): Int {
        val previous = rssiByMessage[messageId] ?: random.nextDouble(-85.0, -55.0)
        val next = (previous + random.nextDouble(-3.0, 3.0)).coerceIn(-95.0, -38.0)
        rssiByMessage[messageId] = next
        return next.roundToInt()
    }

    private fun soundAgeMin(t: Long): Int? {
        val at = soundAtMs ?: return null
        if (sound == SoundType.NONE) return null
        return ((t - at) / 60_000L).toInt().coerceIn(0, FlareConstants.BYTE_CAP)
    }

    private fun newPhoneId() = random.nextInt(1, 0x1_0000)

    private fun emitLog(type: LogType, messageId: Long, phoneId: Int) {
        _log.tryEmit(LogEvent(type, messageId, phoneId, now()))
    }

    // These four match the Contract 5 score checks (80, 70, 61 and 5), so the dashboard
    // sort can be checked by eye. Coordinates are made-up sample values.
    private fun sampleRecords(t: Long) = listOf(
        sample(HANDOFF_SAMPLE_ID, 0x0A01, people = 2, injured = true, trapped = true,
            battery = SAMPLE_A_BATTERY, fix = Fix(27.7172, 85.3240, 12, 1), t = t),
        sample(STANDBY_SAMPLE_ID, 0x0B02, people = 2, trapped = true, battery = 60,
            sound = SoundType.KNOCK, soundAgeMin = 2, t = t),
        sample(0x3C4D5E6FL, 0x0C03, people = 4, water = true, battery = 15,
            fix = Fix(27.7180, 85.3255, 30, 31), firstHeardAt = t - 30 * 60_000L, t = t),
        sample(0x4D5E6F70L, 0x0D04, people = 1, battery = null,
            fix = Fix(27.7165, 85.3231, 8, 0), t = t),
    )

    private fun sample(
        messageId: Long,
        originId: Int,
        people: Int,
        battery: Int?,
        t: Long,
        injured: Boolean = false,
        trapped: Boolean = false,
        water: Boolean = false,
        sound: SoundType = SoundType.NONE,
        soundAgeMin: Int? = null,
        fix: Fix? = null,
        firstHeardAt: Long = t,
    ) = SosRecord(
        messageId = messageId,
        originId = originId,
        carrierId = originId,
        latitude = fix?.latitude,
        longitude = fix?.longitude,
        accuracyM = fix?.accuracyM,
        fixAgeMin = fix?.fixAgeMin,
        people = people,
        injured = injured,
        trapped = trapped,
        water = water,
        medical = false,
        fire = false,
        battery = battery,
        sound = sound,
        soundAgeMin = soundAgeMin,
        firstHeardAt = firstHeardAt,
        lastHeardAt = t,
        lastRssi = null,
        acked = false,
        cancelled = false,
    )

    private companion object {
        const val SIGNAL_TICK_MS = 500L
        const val HANDOFF_EVERY_MS = 120_000L
        const val RECEIPT_DELAY_MS = 3_000L
        const val FAKE_STANDBY_ID = 0x0F0F
        const val FAKE_STANDBY_BATTERY = 74
        const val FAKE_RESCUER_ID = 0x0E0E
        const val SAMPLE_A_BATTERY = 50
        const val HANDOFF_SAMPLE_ID = 0x1A2B3C4DL
        const val STANDBY_SAMPLE_ID = 0x2B3C4D5EL
    }
}
