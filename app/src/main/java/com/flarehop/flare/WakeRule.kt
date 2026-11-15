package com.flarehop.flare

// Contract 3 rule 3: a phone with the app closed joins when it hears an SOS. Receipts, STANDBY
// and HANDOFF packets alone never wake it, and neither does a packet that fails Contract 1.
object WakeRule {
    fun heardSos(packets: List<ByteArray>): Boolean = packets.any { bytes ->
        (PacketCodec.decode(bytes) as? Decoded.Ok)?.packet is SosPacket
    }
}
