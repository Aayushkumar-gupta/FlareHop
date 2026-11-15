package com.flarehop.ui

import com.flarehop.flare.SosRecord

// Shared by the Nearby radar and Track, so both place a survivor in the same spot.
fun sosBlip(record: SosRecord, gps: GpsReference?, names: PhoneNames, smoothedRssi: Int?): RadarBlip? {
    val handedOff = record.carrierId != record.originId
    // After a handoff the GPS point is still the victim's but the signal comes from the carrier,
    // which may be somewhere else, so the two only mix while the victim's own phone is sending.
    val bluetooth = (smoothedRssi ?: record.lastRssi)
        ?.takeIf { !handedOff || gps == null }
        ?.let(LocationEstimator::bluetooth)
    val estimate = LocationEstimator.estimate(gps, bluetooth) ?: return null
    val carrier = if (handedOff) " via ${names.phone(record.carrierId)}" else ""
    return RadarBlip(
        phoneId = record.originId,
        label = "${names.survivor(record.originId)}$carrier",
        estimate = estimate,
        isSos = true,
    )
}
