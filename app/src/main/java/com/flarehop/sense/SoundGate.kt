package com.flarehop.sense

import com.flarehop.flare.SoundType
import kotlin.math.log10
import kotlin.math.sqrt

data class Heard(val label: String, val score: Float)

// Crowd noise is labelled so the phone can show what it hears, but a crowd says nothing about
// whether the person who sent the SOS is alive.
val SoundType.isSignOfLife: Boolean
    get() = this != SoundType.NONE && this != SoundType.CROWD

class SoundGate(private val loudnessFloorDbfs: Float = DEFAULT_LOUDNESS_FLOOR_DBFS) {

    fun decide(heard: List<Heard>, samples: FloatArray): SoundType? {
        if (loudnessDbfs(samples) < loudnessFloorDbfs) return null
        // A second of audio reaches the model as two chunks, so each label counts at its best.
        val best = heard.groupBy { it.label }.mapValues { (_, same) -> same.maxOf { it.score } }
        val candidates = buildList {
            best.forEach { (label, score) ->
                typeFor(label)?.takeIf { it != SoundType.KNOCK }?.let { add(it to score) }
            }
            add(SoundType.KNOCK to best.filterKeys { typeFor(it) == SoundType.KNOCK }.values.sum())
        }
        return candidates.filter { it.second >= MIN_SCORE }.maxByOrNull { it.second }?.first
    }

    companion object {
        // YAMNet splits one knock across Knock, Door, Tap and Wood block (0.15 to 0.4 each on the
        // phones), so the knock labels are added up and the sum is held to this minimum.
        const val MIN_SCORE = 0.3f

        // Measured from the YamnetListener log on a Galaxy A15 and a Motorola Edge 2024: knocks next
        // to the phone peaked at -45 to -53 dBFS and a normal voice at -39 to -51, while a quiet
        // room sat at -50 to -66 and never had a counted label at MIN_SCORE or above.
        const val DEFAULT_LOUDNESS_FLOOR_DBFS = -55f
        private const val FRAME_SAMPLES = 800

        private val CLASSES = mapOf(
            "Speech" to SoundType.SPEECH,
            "Child speech, kid speaking" to SoundType.SPEECH,
            "Conversation" to SoundType.SPEECH,
            "Narration, monologue" to SoundType.SPEECH,
            "Whispering" to SoundType.SPEECH,
            "Shout" to SoundType.SHOUT,
            "Bellow" to SoundType.SHOUT,
            "Whoop" to SoundType.SHOUT,
            "Yell" to SoundType.SHOUT,
            "Screaming" to SoundType.SHOUT,
            "Children shouting" to SoundType.SHOUT,
            "Knock" to SoundType.KNOCK,
            "Tap" to SoundType.KNOCK,
            "Wood" to SoundType.KNOCK,
            "Thump, thud" to SoundType.KNOCK,
            "Bang" to SoundType.KNOCK,
            "Door" to SoundType.KNOCK,
            "Wood block" to SoundType.KNOCK,
            "Crying, sobbing" to SoundType.CRYING,
            "Baby cry, infant cry" to SoundType.CRYING,
            "Whimper" to SoundType.CRYING,
            "Wail, moan" to SoundType.CRYING,
            "Groan" to SoundType.CRYING,
            "Chatter" to SoundType.CROWD,
            "Crowd" to SoundType.CROWD,
            "Hubbub, speech noise, speech babble" to SoundType.CROWD,
            "Cheering" to SoundType.CROWD,
            "Applause" to SoundType.CROWD,
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
