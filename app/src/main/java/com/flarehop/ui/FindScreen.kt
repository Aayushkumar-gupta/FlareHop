package com.flarehop.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    // dB gained over the last few readings: above 0 is warmer. Drives the needle, which moves
    // smoothly where the trend only has three steps.
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
    val gps by flare.locationReader.location.collectAsState()
    val record = known.firstOrNull { it.messageId == messageId }
    val track = tracks[messageId]
    val tracker = remember(messageId) { FindTracker() }
    var reading by remember(messageId) { mutableStateOf<FindReading?>(null) }
    val heading = rememberHeading()
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
    // GPS is only trusted for a direction once the victim is further away than its error.
    val bearing = estimate?.bearingDeg?.takeIf { estimate.distanceM >= MIN_ARROW_M }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = "Track",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (record != null) {
                item {
                    Text(
                        text = "Phone ${phoneLabel(record.originId)}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            item {
                Direction(
                    bearing = bearing,
                    heading = heading,
                    change = current?.change?.takeIf { !lost },
                )
            }
            item {
                Text(
                    text = directionText(lost, current, bearing, heading),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (lost) MaterialTheme.colorScheme.error else Color.Unspecified,
                    textAlign = TextAlign.Center,
                )
            }
            estimate?.let {
                item {
                    Text(
                        text = estimateText(it),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (current != null && !lost) {
                item { SignalMeter(current) }
            } else {
                item {
                    Text(
                        text = "Walk slowly and hold the phone up. Beeps start again once the " +
                            "SOS is heard.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item { SectionTitle("Phones that carried this SOS (${track?.carriers?.size ?: 0})") }
            items(track?.carriers.orEmpty().sortedByDescending { it.isVictim }) { carrier ->
                CarrierRow(carrier = carrier, now = now)
            }
            item { SectionTitle("History") }
            items(track?.history.orEmpty().asReversed()) { entry -> HistoryRow(entry) }
        }
        OutlinedButton(
            onClick = flare::stopFinding,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(56.dp),
        ) {
            Text(text = "Back to the list", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun directionText(
    lost: Boolean,
    reading: FindReading?,
    bearing: Double?,
    heading: Float?,
): String = when {
    bearing != null && heading != null -> "Walk the way the arrow points"
    lost -> if (reading == null) "Waiting for the signal" else "Signal lost"
    else -> trendText(reading!!.trend)
}

private fun estimateText(estimate: LocationEstimate): String {
    val source = when (estimate.source) {
        EstimateSource.GPS -> "by GPS"
        EstimateSource.BLUETOOTH -> "by signal"
        EstimateSource.GPS_AND_BLUETOOTH -> "by GPS and signal"
    }
    return "About ${distanceText(estimate.distanceM)} " +
        "(±${distanceText(estimate.errorM)}), $source"
}

private fun trendText(trend: Trend) = when (trend) {
    Trend.WARMER -> "Warmer"
    Trend.COLDER -> "Colder"
    Trend.STEADY -> "Steady"
}

// With GPS on both phones and a compass, a real arrow toward the victim; otherwise a needle that
// leans toward Warmer or Colder as the signal changes. Both glide instead of jumping.
@Composable
private fun Direction(bearing: Double?, heading: Float?, change: Double?) {
    if (bearing != null && heading != null) {
        CompassArrow(angle = Angles.normalize(bearing.toFloat() - heading))
    } else {
        SignalNeedle(change = change)
    }
}

@Composable
private fun CompassArrow(angle: Float) {
    var target by remember { mutableFloatStateOf(angle) }
    target = Angles.unwrapNear(target, angle)
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = TURN_MS),
        label = "compass arrow",
    )
    val color = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(DIAL_SIZE.dp)) {
        val r = size.minDimension / 2f
        drawCircle(ring, r, center, style = Stroke(2.dp.toPx()))
        rotate(shown, center) {
            val path = Path().apply {
                moveTo(center.x, center.y - r * 0.85f)
                lineTo(center.x + r * 0.35f, center.y + r * 0.45f)
                lineTo(center.x, center.y + r * 0.2f)
                lineTo(center.x - r * 0.35f, center.y + r * 0.45f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun SignalNeedle(change: Double?) {
    val lean = ((change ?: 0.0) / FULL_LEAN_DB).toFloat().coerceIn(-1f, 1f)
    val shown by animateFloatAsState(
        targetValue = lean * MAX_LEAN_DEG,
        animationSpec = tween(durationMillis = TURN_MS),
        label = "signal needle",
    )
    val colder = MaterialTheme.colorScheme.error
    val warmer = WARMER_GREEN
    val steady = MaterialTheme.colorScheme.outline
    val needle = when {
        change == null -> steady
        change >= FindTracker.TREND_DB -> warmer
        -change >= FindTracker.TREND_DB -> colder
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = DIAL_SIZE.dp, height = (DIAL_SIZE / 2 + 12).dp)) {
            val r = size.width / 2f
            val pivot = Offset(size.width / 2f, r)
            val stroke = Stroke(10.dp.toPx())
            val box = Size(r * 2 - stroke.width, r * 2 - stroke.width)
            val corner = Offset(stroke.width / 2, stroke.width / 2)
            drawArc(colder, 180f, 60f, false, corner, box, style = stroke)
            drawArc(steady, 240f, 60f, false, corner, box, style = stroke)
            drawArc(warmer, 300f, 60f, false, corner, box, style = stroke)
            rotate(shown, pivot) {
                drawLine(
                    needle,
                    pivot,
                    Offset(pivot.x, pivot.y - r * 0.8f),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(needle, 8.dp.toPx(), pivot)
        }
        Row(Modifier.width(DIAL_SIZE.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Colder", color = colder, style = MaterialTheme.typography.labelLarge)
            Text("Warmer", color = warmer, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SignalMeter(reading: FindReading) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${(reading.meter * 100).roundToInt()}%",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(
            progress = { reading.meter },
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Text(
            text = "${reading.smoothedRssi.roundToInt()} dBm from phone " +
                phoneLabel(reading.carrierId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

@Composable
private fun CarrierRow(carrier: CarrierSeen, now: Long) {
    val role = if (carrier.isVictim) "victim" else "carrier"
    val battery = carrier.battery?.let { "$it%" } ?: "battery unknown"
    val signal = carrier.rssi?.let { "$it dBm" } ?: "no signal reading"
    val agoSec = ((now - carrier.lastHeardAt) / 1_000).coerceAtLeast(0)
    val ago = if (agoSec < 60) "${agoSec}s ago" else "${agoSec / 60} min ago"
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Phone ${phoneLabel(carrier.phoneId)}, $role",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "$battery, $signal, last heard $ago",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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

private val WARMER_GREEN = Color(0xFF2E7D32)
private val HISTORY_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US).withZone(ZoneId.systemDefault())

// A dB swing this big leans the needle all the way to Warmer or Colder.
private const val FULL_LEAN_DB = 6.0
private const val MAX_LEAN_DEG = 90f
private const val TURN_MS = 400
private const val DIAL_SIZE = 200

// Phone GPS is often 10 to 30 m off, so closer than this the arrow could point anywhere and the
// signal needle leads instead.
private const val MIN_ARROW_M = 30.0
private const val BEEP_MS = 80
private const val BUZZ_MS = 40L
private const val IDLE_CHECK_MS = 200L
