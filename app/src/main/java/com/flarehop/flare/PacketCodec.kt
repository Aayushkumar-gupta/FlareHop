package com.flarehop.flare

import com.flarehop.flare.FlareConstants.BYTE_CAP
import com.flarehop.flare.FlareConstants.NO_FIX
import com.flarehop.flare.FlareConstants.PACKET_SIZE
import com.flarehop.flare.FlareConstants.PROTOCOL_VERSION
import com.flarehop.flare.FlareConstants.TYPE_HANDOFF
import com.flarehop.flare.FlareConstants.TYPE_RECEIPT
import com.flarehop.flare.FlareConstants.TYPE_SOS
import com.flarehop.flare.FlareConstants.TYPE_STANDBY
import com.flarehop.flare.FlareConstants.UNKNOWN_BYTE
import java.nio.ByteBuffer
import kotlin.math.roundToInt

sealed interface Packet

data class SosPacket(
    val messageId: Long,
    val originId: Int,
    val carrierId: Int,
    val sound: SoundType,
    val soundAgeSec: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Int?,
    val fixAgeMin: Int?,
    val details: SosDetails,
    val battery: Int?,
) : Packet

data class ReceiptPacket(val receiptId: Long, val rescuerId: Int, val ackedMessageId: Long) :
    Packet

data class StandbyPacket(
    val messageId: Long,
    val originId: Int,
    val standbyId: Int,
    val battery: Int?,
) : Packet

data class HandoffPacket(
    val messageId: Long,
    val originId: Int,
    val carrierId: Int,
    val successorId: Int,
    val battery: Int?,
) : Packet

sealed interface Decoded {
    data class Ok(val packet: Packet) : Decoded

    data class Dropped(val reason: String) : Decoded
}

object PacketCodec {
    private const val DEGREE_SCALE = 10_000_000.0
    private const val EXACT_SOUND_SECONDS = 120
    private const val SOUND_MINUTE_BASE = 118

    fun encode(packet: Packet): ByteArray {
        val buf = ByteBuffer.allocate(PACKET_SIZE)
        when (packet) {
            is SosPacket -> {
                val hasFix = packet.latitude != null && packet.longitude != null
                buf.put(0, header(TYPE_SOS))
                buf.putInt(1, packet.messageId.toInt())
                buf.putShort(5, packet.originId.toShort())
                buf.putShort(7, packet.carrierId.toShort())
                buf.put(9, soundCode(packet.sound).toByte())
                buf.putInt(10, if (hasFix) degrees(packet.latitude!!) else NO_FIX)
                buf.putInt(14, if (hasFix) degrees(packet.longitude!!) else NO_FIX)
                buf.put(18, capped(packet.accuracyM))
                buf.put(19, if (hasFix) capped(packet.fixAgeMin) else UNKNOWN_BYTE.toByte())
                buf.putShort(20, statusBits(packet.details))
                buf.put(22, batteryByte(packet.battery))
                val soundAge = if (packet.sound == SoundType.NONE) null else packet.soundAgeSec
                buf.put(23, soundAgeByte(soundAge))
            }

            is ReceiptPacket -> {
                buf.put(0, header(TYPE_RECEIPT))
                buf.putInt(1, packet.receiptId.toInt())
                buf.putShort(5, packet.rescuerId.toShort())
                buf.putShort(7, packet.rescuerId.toShort())
                buf.putInt(10, packet.ackedMessageId.toInt())
            }

            is StandbyPacket -> {
                buf.put(0, header(TYPE_STANDBY))
                buf.putInt(1, packet.messageId.toInt())
                buf.putShort(5, packet.originId.toShort())
                buf.putShort(7, packet.standbyId.toShort())
                buf.put(22, batteryByte(packet.battery))
            }

            is HandoffPacket -> {
                buf.put(0, header(TYPE_HANDOFF))
                buf.putInt(1, packet.messageId.toInt())
                buf.putShort(5, packet.originId.toShort())
                buf.putShort(7, packet.carrierId.toShort())
                buf.putShort(10, packet.successorId.toShort())
                buf.put(22, batteryByte(packet.battery))
            }
        }
        return buf.array()
    }

    fun decode(bytes: ByteArray): Decoded {
        if (bytes.size != PACKET_SIZE) return Decoded.Dropped("length ${bytes.size}")
        val buf = ByteBuffer.wrap(bytes)
        val version = u8(buf, 0) shr 4
        val type = u8(buf, 0) and 0x0F
        if (version != PROTOCOL_VERSION) return Decoded.Dropped("version $version")
        val decoded = when (type) {
            TYPE_SOS -> decodeSos(buf)

            TYPE_RECEIPT -> Decoded.Ok(
                ReceiptPacket(
                    receiptId = u32(buf, 1),
                    rescuerId = u16(buf, 5),
                    ackedMessageId = u32(buf, 10),
                ),
            )

            TYPE_STANDBY -> Decoded.Ok(
                StandbyPacket(
                    messageId = u32(buf, 1),
                    originId = u16(buf, 5),
                    standbyId = u16(buf, 7),
                    battery = batteryValue(u8(buf, 22)),
                ),
            )

            TYPE_HANDOFF -> Decoded.Ok(
                HandoffPacket(
                    messageId = u32(buf, 1),
                    originId = u16(buf, 5),
                    carrierId = u16(buf, 7),
                    successorId = u16(buf, 10),
                    battery = batteryValue(u8(buf, 22)),
                ),
            )

            else -> Decoded.Dropped("type $type")
        }
        return if (decoded is Decoded.Ok && hasZeroId(decoded.packet)) {
            Decoded.Dropped("zero id")
        } else {
            decoded
        }
    }

    private fun hasZeroId(packet: Packet): Boolean = when (packet) {
        is SosPacket -> packet.messageId == 0L || packet.originId == 0 || packet.carrierId == 0

        is ReceiptPacket ->
            packet.receiptId == 0L || packet.rescuerId == 0 || packet.ackedMessageId == 0L

        is StandbyPacket -> packet.standbyId == 0

        is HandoffPacket -> packet.messageId == 0L || packet.originId == 0 || packet.carrierId == 0
    }

    private fun decodeSos(buf: ByteBuffer): Decoded {
        val latRaw = buf.getInt(10)
        val lonRaw = buf.getInt(14)
        val noFix = latRaw == NO_FIX && lonRaw == NO_FIX
        val latitude = if (noFix) null else latRaw / DEGREE_SCALE
        val longitude = if (noFix) null else lonRaw / DEGREE_SCALE
        if (latitude != null && longitude != null &&
            (latitude !in -90.0..90.0 || longitude !in -180.0..180.0)
        ) {
            return Decoded.Dropped("position")
        }
        val sound = soundType(u8(buf, 9))
        val status = u16(buf, 20)
        return Decoded.Ok(
            SosPacket(
                messageId = u32(buf, 1),
                originId = u16(buf, 5),
                carrierId = u16(buf, 7),
                sound = sound,
                soundAgeSec = if (sound == SoundType.NONE) null else soundAgeSeconds(u8(buf, 23)),
                latitude = latitude,
                longitude = longitude,
                accuracyM = orNull(u8(buf, 18)),
                fixAgeMin = if (noFix) null else orNull(u8(buf, 19)),
                details = SosDetails(
                    people = status shr 12,
                    injured = status and (1 shl 11) != 0,
                    trapped = status and (1 shl 10) != 0,
                    water = status and (1 shl 9) != 0,
                    medical = status and (1 shl 8) != 0,
                    fire = status and (1 shl 7) != 0,
                ),
                battery = batteryValue(u8(buf, 22)),
            ),
        )
    }

    private fun header(type: Int): Byte = ((PROTOCOL_VERSION shl 4) or type).toByte()

    private fun degrees(value: Double): Int = (value * DEGREE_SCALE).roundToInt()

    private fun capped(value: Int?): Byte = (value?.coerceIn(0, BYTE_CAP) ?: UNKNOWN_BYTE).toByte()

    private fun orNull(value: Int): Int? = if (value == UNKNOWN_BYTE) null else value

    // Byte 23 is exact to the second for the first two minutes, so a rescuer can see "Knocking,
    // 12 s ago", then counts whole minutes up to 136, which covers the 10-minute score window.
    private fun soundAgeByte(seconds: Int?): Byte {
        if (seconds == null) return UNKNOWN_BYTE.toByte()
        val age = seconds.coerceAtLeast(0)
        val value = if (age < EXACT_SOUND_SECONDS) age else SOUND_MINUTE_BASE + age / 60
        return value.coerceAtMost(BYTE_CAP).toByte()
    }

    private fun soundAgeSeconds(value: Int): Int? = when {
        value == UNKNOWN_BYTE -> null
        value < EXACT_SOUND_SECONDS -> value
        else -> (value - SOUND_MINUTE_BASE) * 60
    }

    private fun batteryByte(percent: Int?): Byte =
        (percent?.coerceIn(0, 100) ?: UNKNOWN_BYTE).toByte()

    private fun batteryValue(value: Int): Int? = if (value in 0..100) value else null

    private fun statusBits(details: SosDetails): Short {
        var bits = details.people.coerceIn(0, 15) shl 12
        if (details.injured) bits = bits or (1 shl 11)
        if (details.trapped) bits = bits or (1 shl 10)
        if (details.water) bits = bits or (1 shl 9)
        if (details.medical) bits = bits or (1 shl 8)
        if (details.fire) bits = bits or (1 shl 7)
        return bits.toShort()
    }

    private fun soundCode(sound: SoundType): Int = when (sound) {
        SoundType.NONE -> 0
        SoundType.SPEECH -> 1
        SoundType.SHOUT -> 2
        SoundType.KNOCK -> 3
        SoundType.CRYING -> 4
        SoundType.CROWD -> 5
    }

    // A phone built before code 5 existed reads crowd noise as no sound, which is safe because
    // crowd noise is never a sign of life.
    private fun soundType(code: Int): SoundType = when (code) {
        1 -> SoundType.SPEECH
        2 -> SoundType.SHOUT
        3 -> SoundType.KNOCK
        4 -> SoundType.CRYING
        5 -> SoundType.CROWD
        else -> SoundType.NONE
    }

    private fun u8(buf: ByteBuffer, index: Int): Int = buf.get(index).toInt() and 0xFF

    private fun u16(buf: ByteBuffer, index: Int): Int = buf.getShort(index).toInt() and 0xFFFF

    private fun u32(buf: ByteBuffer, index: Int): Long = buf.getInt(index).toLong() and 0xFFFFFFFFL
}
