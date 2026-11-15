package com.flarehop.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.DirectSignal
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

enum class Trend { WARMER, COLDER, STEADY }

data class FindReading(
    val carrierId: Int,
    val smoothedRssi: Double,
    val meter: Float,
    val trend: Trend,
    val beepIntervalMs: Long,
    val atMs: Long,
)

// Contract 6, kept free of Android so the numbers can be checked without a phone.
class FindTracker {
    private val readings = ArrayDeque<Int>()
    private var carrierId: Int? = null

    fun add(signal: DirectSignal): FindReading {
        // After a handoff the signal comes from a different phone in a different spot, so the
        // old readings would only blur the new direction.
        if (signal.carrierId != carrierId) readings.clear()
        carrierId = signal.carrierId
        readings.addLast(signal.rssi)
        while (readings.size > SMOOTHING_READINGS) readings.removeFirst()
        val smoothed = readings.average()
        val meter = meter(smoothed)
        return FindReading(
            carrierId = signal.carrierId,
            smoothedRssi = smoothed,
            meter = meter,
            trend = trend(),
            beepIntervalMs = beepIntervalMs(meter),
            atMs = signal.atMs,
        )
    }

    private fun trend(): Trend {
        if (readings.size < TREND_READINGS * 2) return Trend.STEADY
        val list = readings.toList()
        val newer = list.takeLast(TREND_READINGS).average()
        val older = list.dropLast(TREND_READINGS).takeLast(TREND_READINGS).average()
        return when {
            newer - older >= TREND_DB -> Trend.WARMER
            older - newer >= TREND_DB -> Trend.COLDER
            else -> Trend.STEADY
        }
    }

    companion object {
        const val SMOOTHING_READINGS = 10
        const val TREND_READINGS = 5
        const val TREND_DB = 3.0
        const val WEAKEST_DBM = -90.0
        const val STRONGEST_DBM = -40.0
        const val SLOWEST_BEEP_MS = 1_500L
        const val FASTEST_BEEP_MS = 150L
        const val LOST_AFTER_MS = 5_000L

        fun meter(rssi: Double): Float =
            ((rssi - WEAKEST_DBM) / (STRONGEST_DBM - WEAKEST_DBM)).toFloat().coerceIn(0f, 1f)

        fun beepIntervalMs(meter: Float): Long =
            (SLOWEST_BEEP_MS - (SLOWEST_BEEP_MS - FASTEST_BEEP_MS) * meter).roundToInt().toLong()
    }
}

@Composable
fun FindScreen(flare: FlareViewModel, messageId: Long, modifier: Modifier = Modifier) {
    val known by flare.engine.knownSos.collectAsState()
    val record = known.firstOrNull { it.messageId == messageId }
    val tracker = remember(messageId) { FindTracker() }
    var reading by remember(messageId) { mutableStateOf<FindReading?>(null) }
    val now = rememberNow()

    LaunchedEffect(messageId) {
        flare.engine.directSignal.collect { signal ->
            if (signal.messageId == messageId) reading = tracker.add(signal)
        }
    }

    val current = reading
    val lost = current == null || now - current.atMs > FindTracker.LOST_AFTER_MS
    Beeper(intervalMs = if (lost) null else current?.beepIntervalMs)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Find",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        if (record != null) {
            Text(
                text = peopleText(record.people),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (lost || current == null) {
            Text(
                text = if (current == null) "Waiting for the signal" else "Signal lost",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Walk slowly and hold the phone up. Beeps start again once the SOS " +
                    "is heard.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        } else {
            TrendArrow(current.trend)
            Text(
                text = trendText(current.trend),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${(current.meter * 100).roundToInt()}%",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { current.meter },
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
            Text(
                text = "${current.smoothedRssi.roundToInt()} dBm from phone " +
                    String.format(Locale.US, "%04X", current.carrierId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = flare::stopFinding,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Back to the list", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun trendText(trend: Trend) = when (trend) {
    Trend.WARMER -> "Warmer"
    Trend.COLDER -> "Colder"
    Trend.STEADY -> "Steady"
}

@Composable
private fun TrendArrow(trend: Trend) {
    val color = when (trend) {
        Trend.WARMER -> WARMER_GREEN
        Trend.COLDER -> MaterialTheme.colorScheme.error
        Trend.STEADY -> MaterialTheme.colorScheme.outline
    }
    Canvas(Modifier.size(ARROW_SIZE.dp)) {
        val w = size.width
        val h = size.height
        val path = Path()
        when (trend) {
            Trend.WARMER -> {
                path.moveTo(w / 2, 0f)
                path.lineTo(w, h)
                path.lineTo(0f, h)
            }

            Trend.COLDER -> {
                path.moveTo(0f, 0f)
                path.lineTo(w, 0f)
                path.lineTo(w / 2, h)
            }

            Trend.STEADY -> {
                drawRect(color, Offset(0f, h * 0.4f), size.copy(height = h * 0.2f))
                return@Canvas
            }
        }
        path.close()
        drawPath(path, color)
    }
}

// One beep and one buzz each interval; a null interval means silence.
@Composable
private fun Beeper(intervalMs: Long?) {
    val context = LocalContext.current
    val interval by rememberUpdatedState(intervalMs)
    val tone =
        remember { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull() }
    val vibrator = remember {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    LaunchedEffect(Unit) {
        while (true) {
            val wait = interval
            if (wait == null) {
                delay(IDLE_CHECK_MS)
                continue
            }
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
            vibrator?.vibrate(
                VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE),
            )
            delay(wait)
        }
    }
}

private val WARMER_GREEN = Color(0xFF2E7D32)
private const val ARROW_SIZE = 120
private const val BEEP_MS = 80
private const val BUZZ_MS = 40L
private const val IDLE_CHECK_MS = 200L
