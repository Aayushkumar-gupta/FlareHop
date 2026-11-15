package com.flarehop.sense

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
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
    // The model answers each window well before the next one is read, so its answer belongs to
    // the latest samples. Matching by timestamp lost every answer whose timestamp MediaPipe moved.
    @Volatile
    private var latest: FloatArray? = null
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
                latest = null
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    // Closing the classifier while classifyAsync is still running crashes in native code,
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

    private fun build(): AudioClassifier? {
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.AUDIO_STREAM)
            .setMaxResults(MAX_RESULTS)
            .setResultListener { result -> onResult(result) }
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
        var lastTimestamp = 0L
        while (isActive) {
            val listen = signs.shouldListen(clock() - startedAt)
            val recording = record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (listen && !recording) record.startRecording()
            if (!listen && recording) record.stop()
            delay(WINDOW_MS)
            if (!listen) continue
            window.load(record)
            val timestamp = maxOf(clock(), lastTimestamp + 1)
            lastTimestamp = timestamp
            latest = window.buffer.copyOf()
            classifier.classifyAsync(window, timestamp)
        }
    }

    // In stream mode MediaPipe hands back only the result, so the loudness gate runs on the
    // samples kept from the window it heard.
    private fun onResult(result: AudioClassifierResult) {
        val samples = latest ?: return
        val heard = result.classificationResults()
            .flatMap { it.classifications() }
            .flatMap { it.categories() }
            .map { Heard(it.categoryName(), it.score()) }
        val type = signs.onWindow(heard, samples, clock())
        val top = heard.maxByOrNull { it.score }
        Log.d(
            TAG,
            "Heard ${top?.label ?: "nothing"} ${top?.score ?: 0f}, loudest " +
                "${SoundGate.loudnessDbfs(samples)} dBFS, sent ${type ?: "nothing"}",
        )
    }

    companion object {
        const val MODEL = "yamnet.tflite"
        const val SAMPLE_RATE = 16_000
        const val WINDOW_MS = 1_000L
        private const val MAX_RESULTS = 5
        private const val TAG = "YamnetListener"
    }
}
