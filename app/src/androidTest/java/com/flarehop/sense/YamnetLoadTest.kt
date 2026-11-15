package com.flarehop.sense

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YamnetLoadTest {
    @Test
    fun modelLoadsAndClassifiesASilentSecond() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(YamnetListener.MODEL).build())
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .setMaxResults(5)
            .build()
        val classifier = AudioClassifier.createFromOptions(context, options)
        val format = AudioData.AudioDataFormat.builder()
            .setNumOfChannels(1)
            .setSampleRate(YamnetListener.SAMPLE_RATE.toFloat())
            .build()
        val second = AudioData.create(format, YamnetListener.SAMPLE_RATE)
        second.load(FloatArray(YamnetListener.SAMPLE_RATE))
        val result = classifier.classify(second)
        classifier.close()
        assertTrue(result.classificationResults().isNotEmpty())
    }
}
