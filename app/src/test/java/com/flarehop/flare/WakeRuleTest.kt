package com.flarehop.flare

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeRuleTest {
    private val sos = PacketCodec.encode(
        SosPacket(
            messageId = 0x12345678L,
            originId = 0x10A1,
            carrierId = 0x10A1,
            sound = SoundType.NONE,
            soundAgeSec = null,
            latitude = null,
            longitude = null,
            accuracyM = null,
            fixAgeMin = null,
            details = SosDetails(
                people = 1,
                injured = false,
                trapped = false,
                water = false,
                medical = false,
                fire = false,
            ),
            battery = 50,
        ),
    )
    private val receipt = PacketCodec.encode(
        ReceiptPacket(receiptId = 7L, rescuerId = 0x20B2, ackedMessageId = 0x12345678L),
    )
    private val standby = PacketCodec.encode(
        StandbyPacket(messageId = 0L, originId = 0, standbyId = 0x30C3, battery = 80),
    )
    private val cancel = PacketCodec.encode(
        HandoffPacket(
            messageId = 0x12345678L,
            originId = 0x10A1,
            carrierId = 0x10A1,
            successorId = 0,
            battery = 50,
        ),
    )

    @Test
    fun anSosWakesThePhone() {
        assertTrue(WakeRule.heardSos(listOf(standby, sos)))
    }

    @Test
    fun receiptsStandbyAndCancelAloneDoNot() {
        assertFalse(WakeRule.heardSos(listOf(receipt, standby, cancel)))
    }

    @Test
    fun aPacketThatFailsTheChecksDoesNot() {
        assertFalse(WakeRule.heardSos(listOf(sos.copyOf(22))))
        val zeroId = sos.copyOf().also {
            it[1] = 0
            it[2] = 0
            it[3] = 0
            it[4] = 0
        }
        assertFalse(WakeRule.heardSos(listOf(zeroId)))
    }

    @Test
    fun nothingHeardDoesNot() {
        assertFalse(WakeRule.heardSos(emptyList()))
    }

    @Test
    fun aWokenServiceStartsAsABluetoothCarrierOnly() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            FlareService.serviceTypes(woken = true, location = true, microphone = true),
        )
    }

    @Test
    fun anOpenAppAddsLocationAndMicrophoneWhenAllowed() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            FlareService.serviceTypes(woken = false, location = true, microphone = true),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            FlareService.serviceTypes(woken = false, location = false, microphone = false),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            FlareService.serviceTypes(woken = false, location = true, microphone = false),
        )
    }
}
