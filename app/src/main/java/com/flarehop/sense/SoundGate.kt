package com.flarehop.sense

import com.flarehop.flare.SoundType
import kotlin.math.log10
import kotlin.math.sqrt

data class Heard(val label: String, val score: Float)

class SoundGate(private val loudnessFloorDbfs: Float = DEFAULT_LOUDNESS_FLOOR_DBFS) {

    fun decide(heard: List<Heard>, samples: FloatArray): SoundType? {
        if (loudnessDbfs(samples) < loudnessFloorDbfs) return null
        return heard
            .filter { it.score >= MIN_SCORE }
            .mapNotNull { h -> typeFor(h.label)?.let { it to h.score } }
            .maxByOrNull { it.second }
            ?.first
    }

    companion object {
        const val MIN_SCORE = 0.5f

        // Set from 16 kHz clips: every knock second peaked at -22 dBFS or louder, most crowd
        // seconds at -28 or quieter. Re-measure on the demo phones at the hall test.
        const val DEFAULT_LOUDNESS_FLOOR_DBFS = -25f
        private const val FRAME_SAMPLES = 800

        private val CLASSES = mapOf(
            "Speech" to SoundType.SPEECH,
            "Shout" to SoundType.SHOUT,
            "Yell" to SoundType.SHOUT,
            "Screaming" to SoundType.SHOUT,
            "Children shouting" to SoundType.SHOUT,
            "Knock" to SoundType.KNOCK,
            "Tap" to SoundType.KNOCK,
            "Crying, sobbing" to SoundType.CRYING,
            "Baby cry, infant cry" to SoundType.CRYING,
            "Whimper" to SoundType.CRYING,
        )

        fun typeFor(label: String): SoundType? = CLASSES[label]

        // A knock lasts a few milliseconds, so averaging over the whole window would bury it;
        // use the loudest 50 ms frame instead.
        fun loudnessDbfs(samples: FloatArray): Float {
            val loudest = samples.asList()
                .chunked(FRAME_SAMPLES)
                .maxOfOrNull { frame -> sqrt(frame.sumOf { it.toDouble() * it } / frame.size) }
                ?: 0.0
            return if (loudest == 0.0) Float.NEGATIVE_INFINITY else (20 * log10(loudest)).toFloat()
        }
    }
}
