package com.flarehop.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.DirectSignal
import com.flarehop.ui.theme.Aegean
import com.flarehop.ui.theme.Bronze
import com.flarehop.ui.theme.BronzeDim
import com.flarehop.ui.theme.Ember
import com.flarehop.ui.theme.EmberCore
import com.flarehop.ui.theme.Inscription
import com.flarehop.ui.theme.Mist
import com.flarehop.ui.theme.NightSea
import com.flarehop.ui.theme.Parchment
import com.flarehop.ui.theme.Tide
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

enum class Trend { WARMER, COLDER, STEADY }

data class FindReading(
    val carrierId: Int,
    val smoothedRssi: Double,
    val meter: Float,
    val trend: Trend,
    // dB gained over the last few readings: above 0 is warmer. Drives the gauge needle, which
    // moves smoothly where the trend only has three steps.
    val change: Double,
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
            change = change(),
            beepIntervalMs = beepIntervalMs(meter),
            atMs = signal.atMs,
        )
    }

    private fun change(): Double {
        if (readings.size < TREND_READINGS * 2) return 0.0
        val list = readings.toList()
        val newer = list.takeLast(TREND_READINGS).average()
        val older = list.dropLast(TREND_READINGS).takeLast(TREND_READINGS).average()
        return newer - older
    }

    private fun trend(): Trend {
        val change = change()
        return when {
            change >= TREND_DB -> Trend.WARMER
            -change >= TREND_DB -> Trend.COLDER
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
    val tracks by flare.tracks.collectAsState()
    val names by flare.names.collectAsState()
    val gps by flare.locationReader.location.collectAsState()
    val record = known.firstOrNull { it.messageId == messageId }
    val track = tracks[messageId]
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

    val here = gps?.toGpsFix()
    val there = record?.let(::fixOf)
    val gpsReference = here?.let { h -> there?.let { LocationEstimator.gps(h, it) } }
    // After a handoff the signal comes from the carrier, not from where the victim is.
    val handedOff = record != null && record.carrierId != record.originId
    val bluetooth = current?.takeIf { !lost && (!handedOff || gpsReference == null) }
        ?.let { LocationEstimator.bluetooth(it.smoothedRssi.roundToInt()) }
    val estimate = LocationEstimator.estimate(gpsReference, bluetooth)

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Text(text = "TRACK", style = Inscription, color = Bronze) }
            if (record != null) {
                item {
                    Text(
                        text = names.survivorWithCode(record.originId) +
                            if (handedOff) ", via ${names.phoneWithCode(record.carrierId)}" else "",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            item { SignalMeter(reading = current?.takeIf { !lost }) }
            estimate?.let {
                item {
                    Text(
                        text = "About ${distanceText(it.distanceM)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                Text(
                    text = when {
                        current == null ->
                            "Waiting for the signal. Walk slowly and hold the phone up."

                        lost -> "Signal lost. Walk slowly and hold the phone up."

                        else ->
                            "Walk slowly. The bar fills and the beeps speed up as you get closer."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (lost) Ember else Mist,
                    textAlign = TextAlign.Center,
                )
            }
            track?.carriers?.takeIf { it.isNotEmpty() }?.let { carriers ->
                item { SectionTitle("Phones that carried it") }
                items(carriers.sortedByDescending { it.isVictim }) { CarrierRow(it, names, now) }
            }
            track?.history?.takeIf { it.isNotEmpty() }?.let { history ->
                item { SectionTitle("Latest") }
                items(history.takeLast(HISTORY_SHOWN).asReversed()) { HistoryRow(it) }
            }
        }
        OutlinedButton(
            onClick = flare::stopFinding,
            border = BorderStroke(1.dp, BronzeDim),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(56.dp),
        ) {
            Text(text = "Back to the list", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// Warmer is the fire, colder the sea. The word, the percentage and a thick bar that glides to each
// new reading, so the rescuer can read it at arm's length while walking.
@Composable
private fun SignalMeter(reading: FindReading?) {
    val level by animateFloatAsState(
        targetValue = reading?.meter ?: 0f,
        animationSpec = tween(durationMillis = METER_MS),
        label = "signal meter",
    )
    val (word, color) = when (reading?.trend) {
        null -> "No signal" to BronzeDim
        Trend.WARMER -> "Warmer" to EmberCore
        Trend.COLDER -> "Colder" to Aegean
        Trend.STEADY -> "Steady" to Parchment
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SignalGauge(change = reading?.change)
        Text(text = word, style = MaterialTheme.typography.displaySmall, color = color)
        Text(
            text = reading?.let { "${(level * 100).roundToInt()}%" } ?: "--",
            style = MaterialTheme.typography.displayLarge,
            color = Parchment,
        )
        Canvas(Modifier.fillMaxWidth().height(METER_HEIGHT.dp)) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(Tide, cornerRadius = radius)
            if (level > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Aegean, Bronze, Ember)),
                    size = Size(size.width * level, size.height),
                    cornerRadius = radius,
                )
            }
            drawRoundRect(BronzeDim, cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Far", style = MaterialTheme.typography.labelLarge, color = Mist)
            Text("Close", style = MaterialTheme.typography.labelLarge, color = Mist)
        }
    }
}

// A dashboard gauge: a half dial from Colder (the sea) through Steady to Warmer (the fire), with a
// needle that glides to how fast the signal is rising or falling.
@Composable
private fun SignalGauge(change: Double?) {
    val lean = ((change ?: 0.0) / FULL_LEAN_DB).toFloat().coerceIn(-1f, 1f)
    val shown by animateFloatAsState(
        targetValue = lean * MAX_LEAN_DEG,
        animationSpec = tween(durationMillis = METER_MS),
        label = "signal gauge",
    )
    val needle = when {
        change == null -> BronzeDim
        change >= FindTracker.TREND_DB -> EmberCore
        -change >= FindTracker.TREND_DB -> Aegean
        else -> Bronze
    }
    Canvas(Modifier.size(width = GAUGE_WIDTH.dp, height = (GAUGE_WIDTH / 2 + 16).dp)) {
        val r = size.width / 2f
        val pivot = Offset(size.width / 2f, r)
        val stroke = Stroke(14.dp.toPx())
        val corner = Offset(stroke.width / 2, stroke.width / 2)
        val box = Size(r * 2 - stroke.width, r * 2 - stroke.width)
        drawArc(Aegean, 180f, 58f, false, corner, box, style = stroke)
        drawArc(BronzeDim, 241f, 58f, false, corner, box, style = stroke)
        drawArc(Ember, 302f, 58f, false, corner, box, style = stroke)
        rotate(shown, pivot) {
            drawLine(
                needle,
                pivot,
                Offset(pivot.x, pivot.y - r * 0.78f),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(Bronze, 10.dp.toPx(), pivot)
        drawCircle(NightSea, 4.dp.toPx(), pivot)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

@Composable
private fun CarrierRow(carrier: CarrierSeen, names: PhoneNames, now: Long) {
    val role = if (carrier.isVictim) "victim" else "carrier"
    val battery = carrier.battery?.let { ", $it%" } ?: ""
    val connected = HISTORY_TIME.format(Instant.ofEpochMilli(carrier.connectedAt))
    val agoSec = ((now - carrier.lastHeardAt) / 1_000).coerceAtLeast(0)
    val ago = if (agoSec < 60) "${agoSec}s ago" else "${agoSec / 60} min ago"
    val name = names.phoneWithCode(carrier.phoneId)
    Text(
        text = "$name, $role$battery, connected $connected, last heard $ago",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = HISTORY_TIME.format(Instant.ofEpochMilli(entry.atMs)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = entry.text, style = MaterialTheme.typography.bodyLarge)
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

private val HISTORY_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(ZoneId.systemDefault())

private const val HISTORY_SHOWN = 5
private const val METER_MS = 400
private const val GAUGE_WIDTH = 240

// A dB swing this big leans the needle all the way to Warmer or Colder.
private const val FULL_LEAN_DB = 6.0
private const val MAX_LEAN_DEG = 90f
private const val METER_HEIGHT = 36
private const val BEEP_MS = 80
private const val BUZZ_MS = 40L
private const val IDLE_CHECK_MS = 200L
