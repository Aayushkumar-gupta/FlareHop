package com.flarehop.flare

enum class SoundType { NONE, SPEECH, SHOUT, KNOCK, CRYING, CROWD }

data class SosDetails(
    val people: Int,
    val injured: Boolean,
    val trapped: Boolean,
    val water: Boolean,
    val medical: Boolean,
    val fire: Boolean,
    // Started because another phone asked this one to, not by its owner tapping SOS.
    val startedRemotely: Boolean = false,
)

data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Int?,
    val fixAgeMin: Int,
)

data class SosRecord(
    val messageId: Long,
    val originId: Int,
    val carrierId: Int,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Int?,
    val fixAgeMin: Int?,
    val people: Int,
    val injured: Boolean,
    val trapped: Boolean,
    val water: Boolean,
    val medical: Boolean,
    val fire: Boolean,
    val battery: Int?,
    val sound: SoundType,
    val soundAgeSec: Int?,
    val firstHeardAt: Long,
    val lastHeardAt: Long,
    val lastRssi: Int?,
    val acked: Boolean,
    val cancelled: Boolean,
    val startedRemotely: Boolean = false,
    // A rescuer reached this person and said so; the SOS no longer needs carrying.
    val rescued: Boolean = false,
)
