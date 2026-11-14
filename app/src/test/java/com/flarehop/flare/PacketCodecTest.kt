package com.flarehop.flare

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PacketCodecTest {
    private val details = SosDetails(
        people = 2,
        injured = true,
        trapped = true,
        water = false,
        medical = false,
        fire = false,
    )

    private val sos = SosPacket(
        messageId = 0xDEADBEEFL,
        originId = 0x1234,
        carrierId = 0x1234,
        sound = SoundType.KNOCK,
        soundAgeMin = 2,
        latitude = 11.2588,
        longitude = 75.7804,
        accuracyM = 12,
        fixAgeMin = 3,
        details = details,
        battery = 47,
    )

    private fun decoded(bytes: ByteArray): Packet = (PacketCodec.decode(bytes) as Decoded.Ok).packet

    private fun dropReason(bytes: ByteArray): String =
        (PacketCodec.decode(bytes) as Decoded.Dropped).reason

    private fun u8(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xFF

    @Test
    fun sosRoundTrip() {
        val bytes = PacketCodec.encode(sos)
        assertEquals(24, bytes.size)
        assertEquals(sos, decoded(bytes))
    }

    @Test
    fun sosByteLayout() {
        val bytes = PacketCodec.encode(sos)
        assertEquals(0x21, u8(bytes, 0))
        assertEquals(0xDE, u8(bytes, 1))
        assertEquals(0xEF, u8(bytes, 4))
        assertEquals(3, u8(bytes, 9))
        assertEquals(0x2C, u8(bytes, 20))
        assertEquals(0x00, u8(bytes, 21))
        assertEquals(47, u8(bytes, 22))
        assertEquals(2, u8(bytes, 23))
    }

    @Test
    fun largestMessageIdSurvives() {
        val packet = sos.copy(messageId = 0xFFFFFFFFL)
        assertEquals(0xFFFFFFFFL, (decoded(PacketCodec.encode(packet)) as SosPacket).messageId)
    }

    @Test
    fun noFixUsesSmallestIntAndDecodesToNull() {
        val bytes = PacketCodec.encode(sos.copy(latitude = null, longitude = null))
        val back = decoded(bytes) as SosPacket
        assertEquals(0x80, u8(bytes, 10))
        assertEquals(0x80, u8(bytes, 14))
        assertEquals(255, u8(bytes, 19))
        assertNull(back.latitude)
        assertNull(back.longitude)
        assertNull(back.fixAgeMin)
    }

    @Test
    fun valuesAboveTheCapAreClamped() {
        val packet = sos.copy(accuracyM = 900, fixAgeMin = 600, soundAgeMin = 300)
        val bytes = PacketCodec.encode(packet)
        assertEquals(254, u8(bytes, 18))
        assertEquals(254, u8(bytes, 19))
        assertEquals(254, u8(bytes, 23))
    }

    @Test
    fun unknownBatteryAndNoSoundUse255() {
        val packet = sos.copy(battery = null, sound = SoundType.NONE, soundAgeMin = 5)
        val bytes = PacketCodec.encode(packet)
        val back = decoded(bytes) as SosPacket
        assertEquals(0, u8(bytes, 9))
        assertEquals(255, u8(bytes, 22))
        assertEquals(255, u8(bytes, 23))
        assertNull(back.battery)
        assertNull(back.soundAgeMin)
    }

    @Test
    fun unknownSoundCodeReadsAsNone() {
        val bytes = PacketCodec.encode(sos)
        bytes[9] = 9
        assertEquals(SoundType.NONE, (decoded(bytes) as SosPacket).sound)
    }

    @Test
    fun unknownStatusBitsAreIgnored() {
        val bytes = PacketCodec.encode(sos)
        bytes[21] = 0x7F
        assertEquals(details, (decoded(bytes) as SosPacket).details)
    }

    @Test
    fun receiptLayout() {
        val receipt = ReceiptPacket(
            receiptId = 77L,
            rescuerId = 0x0A0B,
            ackedMessageId = 0xDEADBEEFL,
        )
        val bytes = PacketCodec.encode(receipt)
        val expected = ByteArray(24)
        expected[0] = 0x22
        expected[4] = 77
        expected[5] = 0x0A
        expected[6] = 0x0B
        expected[7] = 0x0A
        expected[8] = 0x0B
        expected[10] = 0xDE.toByte()
        expected[11] = 0xAD.toByte()
        expected[12] = 0xBE.toByte()
        expected[13] = 0xEF.toByte()
        assertArrayEquals(expected, bytes)
        assertEquals(receipt, decoded(bytes))
    }

    @Test
    fun standbyRoundTrip() {
        val standby = StandbyPacket(messageId = 5L, originId = 10, standbyId = 20, battery = 71)
        val bytes = PacketCodec.encode(standby)
        assertEquals(0x23, u8(bytes, 0))
        assertEquals(standby, decoded(bytes))
    }

    @Test
    fun handoffRoundTripAndCancel() {
        val handoff = HandoffPacket(
            messageId = 5L,
            originId = 10,
            carrierId = 10,
            successorId = 20,
            battery = 8,
        )
        assertEquals(0x24, u8(PacketCodec.encode(handoff), 0))
        assertEquals(handoff, decoded(PacketCodec.encode(handoff)))
        val cancel = handoff.copy(successorId = 0)
        assertEquals(0, (decoded(PacketCodec.encode(cancel)) as HandoffPacket).successorId)
    }

    @Test
    fun dropsWrongLength() {
        assertEquals("length 23", dropReason(ByteArray(23)))
    }

    @Test
    fun dropsUnknownVersion() {
        val bytes = PacketCodec.encode(sos)
        bytes[0] = 0x31
        assertEquals("version 3", dropReason(bytes))
    }

    @Test
    fun dropsUnknownType() {
        val bytes = PacketCodec.encode(sos)
        bytes[0] = 0x25
        assertEquals("type 5", dropReason(bytes))
    }

    @Test
    fun dropsOutOfRangePosition() {
        assertEquals("position", dropReason(PacketCodec.encode(sos.copy(latitude = 91.0))))
        assertEquals("position", dropReason(PacketCodec.encode(sos.copy(longitude = -181.0))))
    }
}
