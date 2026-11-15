package com.flarehop.flare

object FlareConstants {
    const val PACKET_SIZE = 24
    const val PROTOCOL_VERSION = 2
    const val COMPANY_ID = 0xFFFF

    const val TYPE_SOS = 1
    const val TYPE_RECEIPT = 2
    const val TYPE_STANDBY = 3
    const val TYPE_HANDOFF = 4

    // A missing fix is sent as Int.MIN_VALUE, never 0,0, which is a real place in the ocean.
    const val NO_FIX = Int.MIN_VALUE
    const val UNKNOWN_BYTE = 255
    const val BYTE_CAP = 254

    const val HANDOFF_BATTERY_PERCENT = 10
    const val TAKEOVER_SILENCE_MS = 15_000L
    const val TAKEOVER_STEP_MS = 5_000L
    const val JOIN_RSSI_DBM = -70
    const val IN_RANGE_MS = 15_000L
    const val STANDBY_INTERVAL_MS = 5_000L
    const val HANDOFF_RETRY_MS = 30_000L
    const val CANCEL_ADVERT_MS = 10_000L
    const val RECEIPT_KEEP_MS = 15_000L
    const val LOW_POWER_BATTERY_PERCENT = 30
    const val ADVERT_ROTATION_MS = 300L
}
