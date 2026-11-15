package com.flarehop.flare

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class Role { SURVIVOR, RESCUER }

enum class SosStatus { SENDING, HANDED_OFF }

data class MySos(val record: SosRecord, val status: SosStatus)

data class GroupMember(
    val phoneId: Int,
    val battery: Int?,
    val rssi: Int?,
    val lastHeardAt: Long,
    val carriedCount: Int,
)

data class DirectSignal(val messageId: Long, val carrierId: Int, val rssi: Int, val atMs: Long)

enum class LogType {
    HEARD,
    DROPPED,
    JOINED,
    HANDOFF_SENT,
    TOOK_OVER,
    BACKED_OFF,
    RECEIPT_SENT,
    RECEIPT_MATCHED,
    CANCELLED,
}

data class LogEvent(
    val type: LogType,
    val messageId: Long,
    val phoneId: Int,
    val atMs: Long,
    val reason: String? = null,
)

interface FlareEngine {
    val mySos: StateFlow<MySos?>
    val knownSos: StateFlow<List<SosRecord>>
    val carried: StateFlow<List<SosRecord>>
    val group: StateFlow<List<GroupMember>>
    val directSignal: SharedFlow<DirectSignal>
    val log: SharedFlow<LogEvent>

    fun onPacket(bytes: ByteArray, rssi: Int, atMs: Long)

    fun sendSos(details: SosDetails, fix: Fix?)

    fun cancelSos()

    fun setRole(role: Role)

    fun setBattery(percent: Int)

    fun setFix(fix: Fix)

    fun setSound(type: SoundType, atMs: Long)

    fun nextPacket(): ByteArray?

    fun reset()
}
