package com.flarehop.flare

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class Role { SURVIVOR, RESCUER }

enum class SosStatus { SENDING, HANDED_OFF, RESCUED }

// startedBy: the phone that asked this one to start its SOS, or null when its owner tapped.
// rescuedBy and rescuedAt: the rescuer that marked this person as rescued, and when.
data class MySos(
    val record: SosRecord,
    val status: SosStatus,
    val startedBy: Int? = null,
    val rescuedBy: Int? = null,
    val rescuedAt: Long? = null,
)

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
    STARTED_REMOTELY,
    STOPPED_REMOTELY,
    RESCUED,
    RESCUE_NOT_CONFIRMED,
}

data class LogEvent(
    val type: LogType,
    val messageId: Long,
    val phoneId: Int,
    val atMs: Long,
    val reason: String? = null,
)

interface FlareEngine {
    // This phone's random ID for the session; reset picks a new one.
    val phoneId: Int

    val mySos: StateFlow<MySos?>
    val knownSos: StateFlow<List<SosRecord>>
    val carried: StateFlow<List<SosRecord>>
    val group: StateFlow<List<GroupMember>>
    val directSignal: SharedFlow<DirectSignal>
    val log: SharedFlow<LogEvent>

    // Phones this phone is asking to start their SOS right now.
    val pendingCalls: StateFlow<Set<Int>>

    fun onPacket(bytes: ByteArray, rssi: Int, atMs: Long)

    fun sendSos(details: SosDetails, fix: Fix?)

    fun cancelSos()

    // Rescuer only: ask the phone with this ID to start its own SOS, or to stop one that was
    // started that way (Contract 3, rule 11). Only survivor phones answer, and an SOS its owner
    // tapped can never be stopped from another phone.
    fun startSosOn(phoneId: Int)

    fun stopSosOn(phoneId: Int)

    // Rescuer only: this person has been reached (Contract 3, rule 12).
    fun markRescued(messageId: Long)

    fun setRole(role: Role)

    fun setBattery(percent: Int)

    fun setFix(fix: Fix)

    fun setSound(type: SoundType, atMs: Long)

    fun nextPacket(): ByteArray?

    fun reset()
}
