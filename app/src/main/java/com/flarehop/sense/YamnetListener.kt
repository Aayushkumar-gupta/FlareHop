package com.flarehop.sense

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import com.flarehop.flare.SoundType
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class YamnetListener(
    private val context: Context,
    private val signs: SignsOfLife,
    private val onListening: (Boolean) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val failures = CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "Sound listener stopped", e)
    }

    @Volatile
    private var classifier: AudioClassifier? = null
    private var job: Job? = null

    // Loading the model takes about a second, so it happens off the caller's thread and only
    // once; the mic opens after the model is ready and after the previous mic is released.
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val previous = job
        job = scope.launch(Dispatchers.Default + failures) {
            previous?.join()
            val ready = classifier ?: build() ?: return@launch
            ensureActive()
            val record = ready.createAudioRecord(1, SAMPLE_RATE, SAMPLE_RATE)
            try {
                onListening(true)
                loop(ready, record)
            } finally {
                onListening(false)
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                record.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    // Closing the classifier while a window is being classified crashes in native code,
    // so it waits for the loop to finish.
    fun close() {
        val last = job
        stop()
        if (last == null) release() else last.invokeOnCompletion { release() }
    }

    private fun release() {
        classifier?.close()
        classifier = null
    }

    // Clip mode, not stream mode: in stream mode MediaPipe stamps each result with its own frame
    // time (975 ms steps from the first block), so results cannot be matched back to the second
    // of audio that the loudness gate has to check.
    private fun build(): AudioClassifier? {
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .setMaxResults(MAX_RESULTS)
            .build()
        return runCatching { AudioClassifier.createFromOptions(context, options) }
            .onFailure { Log.w(TAG, "Sound model did not load", it) }
            .getOrNull()
            ?.also { classifier = it }
    }

    private suspend fun CoroutineScope.loop(classifier: AudioClassifier, record: AudioRecord) {
        val format = AudioData.AudioDataFormat.builder()
            .setNumOfChannels(1)
            .setSampleRate(SAMPLE_RATE.toFloat())
            .build()
        val window = AudioData.create(format, SAMPLE_RATE)
        val startedAt = clock()
        while (isActive) {
            val listen = signs.shouldListen(clock() - startedAt)
            val recording = record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (listen && !recording) record.startRecording()
            if (!listen && recording) record.stop()
            delay(WINDOW_MS)
            if (!listen) continue
            window.load(record)
            val samples = window.buffer.copyOf()
            val result = runCatching { classifier.classify(window) }
                .onFailure { Log.w(TAG, "Window not classified", it) }
                .getOrNull() ?: continue
            val heard = result.classificationResults()
                .flatMap { it.classifications() }
                .flatMap { it.categories() }
                .map { Heard(it.categoryName(), it.score()) }
            val type = signs.onWindow(heard, samples, clock())
            Log.d(TAG, describe(heard, samples, type))
        }
    }

    // One line per window, so the gate's thresholds can be set from what the phone really hears.
    private fun describe(heard: List<Heard>, samples: FloatArray, type: SoundType?): String {
        val top = heard.sortedByDescending { it.score }
            .take(LOGGED_LABELS)
            .joinToString { String.format(Locale.US, "%s %.2f", it.label, it.score) }
        val dbfs = String.format(Locale.US, "%.0f", SoundGate.loudnessDbfs(samples))
        return "Heard $top at $dbfs dBFS -> ${type ?: "nothing"}"
    }

    companion object {
        const val MODEL = "yamnet.tflite"
        const val SAMPLE_RATE = 16_000
        const val WINDOW_MS = 1_000L
        private const val MAX_RESULTS = 5
        private const val LOGGED_LABELS = 3
        private const val TAG = "YamnetListener"
    }
}
