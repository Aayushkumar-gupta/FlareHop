package com.flarehop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.Role
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import com.flarehop.sense.PriorityBand
import com.flarehop.sense.PriorityScore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

data class Position(val latitude: Double, val longitude: Double)

@Composable
fun SosListScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val role by flare.role.collectAsState()
    val radio = rememberRadioState()

    when {
        role != Role.RESCUER -> BecomeRescuer(onClick = flare::becomeRescuer, modifier = modifier)

        radio != RadioState.READY -> RadioProblem(
            state = radio,
            onStop = flare::stopRescuing,
            modifier = modifier,
        )

        else -> RescuerDashboard(flare = flare, modifier = modifier)
    }
}

@Composable
private fun BecomeRescuer(onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Rescuer",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "This phone will list every SOS it hears, most urgent first, and tell each " +
                "victim that a rescuer received it.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "It stays a rescuer on every tab until you stop.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text(text = "Set yourself as rescuer", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun RadioProblem(state: RadioState, onStop: () -> Unit, modifier: Modifier) {
    val text = radioProblemText(state) ?: return
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            text = text.hint,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { openRadioSettings(context, state) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = text.action, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onStop) { Text("Stop being a rescuer") }
    }
}

// "Heard 12 s ago" and the time part of the priority score only move if something redraws.
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CLOCK_TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun RescuerDashboard(flare: FlareViewModel, modifier: Modifier) {
    val records by flare.engine.knownSos.collectAsState()
    val gps by flare.locationReader.location.collectAsState()
    val names by flare.names.collectAsState()
    val signals by flare.signals.collectAsState()
    val now = rememberNow()

    val here = gps?.let { Position(it.latitude, it.longitude) }

    val (active, cancelled) = rankForDashboard(records, now)
    val newest = active + cancelled

    Column(modifier.fillMaxSize()) {
        DashboardHeader(activeCount = active.size, onStop = flare::stopRescuing)
        if (newest.isEmpty()) {
            EmptyDashboard()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(active, key = { _, it -> it.originId }) { index, record ->
                    SosCard(
                        record = record,
                        names = names,
                        rssi = signals[record.messageId] ?: record.lastRssi,
                        rank = index + 1,
                        now = now,
                        here = here,
                        onFind = { flare.find(record.messageId) },
                    )
                }
                itemsIndexed(cancelled, key = { _, it -> it.originId }) { _, record ->
                    SosCard(
                        record = record,
                        names = names,
                        rssi = signals[record.messageId] ?: record.lastRssi,
                        rank = null,
                        now = now,
                        here = here,
                        onFind = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(activeCount: Int, onStop: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (activeCount == 1) "1 SOS heard" else "$activeCount SOS heard",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ListeningDot()
                        Text(
                            text = "Listening. Receipts on.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun ListeningDot() {
    val pulse by rememberInfiniteTransition(label = "listening").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "listening alpha",
    )
    Box(
        Modifier
            .size(10.dp)
            .alpha(pulse)
            .background(LISTENING_GREEN, CircleShape),
    )
}

@Composable
private fun EmptyDashboard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Listening for SOS signals",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Keep Bluetooth on and move through the area. Every SOS this phone hears " +
                "shows up here, most urgent first.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SosCard(
    record: SosRecord,
    names: PhoneNames,
    rssi: Int?,
    rank: Int?,
    now: Long,
    here: Position?,
    onFind: (() -> Unit)?,
) {
    val band = band(record, now)
    val stripe = if (record.cancelled) MaterialTheme.colorScheme.outline else bandColor(band)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (record.cancelled) CANCELLED_ALPHA else 1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(stripe),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardTop(record = record, band = band, rank = rank)
                Text(
                    text = names.survivor(record.originId),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                InfoRow("Sound", soundText(record))
                InfoRow("Location", locationText(record, here))
                InfoRow("Last signal", lastSignalText(record, now))
                InfoRow("Battery", record.battery?.let { "$it%" } ?: "Unknown")
                InfoRow("Signal", signalText(rssi))
                // Shown only after a handoff, so the rescuer can see the SOS now comes from a
                // different phone than the victim's own.
                if (record.carrierId != record.originId) {
                    InfoRow("Carrier", "${names.phone(record.carrierId)} carries it now")
                }
                ReceiptLine(record)
                if (onFind != null) {
                    Button(onClick = onFind, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(text = "Track", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTop(record: SosRecord, band: PriorityBand, rank: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (record.cancelled) {
            Pill(text = "CANCELLED", color = MaterialTheme.colorScheme.outline)
        } else {
            Pill(text = bandName(band).uppercase(Locale.US), color = bandColor(band))
        }
        Spacer(Modifier.weight(1f))
        if (rank != null) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(INFO_LABEL_WIDTH.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReceiptLine(record: SosRecord) {
    val (text, color) = when {
        record.cancelled -> "Cancelled by the victim" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Receipt sent to the victim" to LISTENING_GREEN
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

private fun band(record: SosRecord, now: Long) =
    PriorityScore.band(PriorityScore.score(record, now))

private fun bandName(band: PriorityBand) = when (band) {
    PriorityBand.CRITICAL -> "Critical"
    PriorityBand.HIGH -> "High"
    PriorityBand.NORMAL -> "Normal"
}

// Contract 5 colours: Critical red, High orange, Normal plain.
@Composable
private fun bandColor(band: PriorityBand): Color = when (band) {
    PriorityBand.CRITICAL -> MaterialTheme.colorScheme.error
    PriorityBand.HIGH -> HIGH_ORANGE
    PriorityBand.NORMAL -> MaterialTheme.colorScheme.secondary
}

fun soundText(record: SosRecord): String {
    val label = when (record.sound) {
        SoundType.NONE -> return "No sound detected yet"
        SoundType.SPEECH -> "Speech"
        SoundType.SHOUT -> "Shouting"
        SoundType.KNOCK -> "Knocking"
        SoundType.CRYING -> "Crying"
    }
    val age = record.soundAgeMin
    return when {
        age == null -> label
        age == 0 -> "$label, just now"
        else -> "$label, $age min ago"
    }
}

fun locationText(record: SosRecord, here: Position?): String {
    val lat = record.latitude
    val lon = record.longitude
    if (lat == null || lon == null) return "No GPS fix. Follow the signal."
    val age = record.fixAgeMin?.let { if (it == 0) ", fix just now" else ", fix $it min old" } ?: ""
    val accuracy = record.accuracyM?.let { " (±$it m)" } ?: ""
    if (here == null) {
        return String.format(Locale.US, "%.5f, %.5f", lat, lon) + accuracy + age
    }
    val meters = distanceMeters(here.latitude, here.longitude, lat, lon)
    val bearing = bearingDegrees(here.latitude, here.longitude, lat, lon)
    return "${distanceText(meters)} ${compassPoint(bearing)}$accuracy$age"
}

fun compassPoint(bearing: Double): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return points[((bearing / 45.0).roundToInt()) % points.size]
}

// About the radio, not sound: how long since this phone last heard the SOS packet. The packet
// has no timestamp field (Contract 1), so the first time shown is when this phone first heard it.
private fun lastSignalText(record: SosRecord, now: Long): String {
    val first = HEARD_AT.format(Instant.ofEpochMilli(record.firstHeardAt))
    return "${agoText(now - record.lastHeardAt)}, first heard $first"
}

// Copies arrive several times a second, so a per-second count would flicker between 0 and 1.
// Ten-second steps still show at a glance whether the phone is in range.
fun agoText(elapsedMs: Long): String {
    val sec = (elapsedMs / 1_000).coerceAtLeast(0)
    return when {
        sec < 10 -> "just now"
        sec < 60 -> "${sec / 10 * 10}s ago"
        else -> "${sec / 60} min ago"
    }
}

// Same scale as Find mode (Contract 6): -90 dBm or weaker is 0%, -40 dBm or stronger is 100%.
fun signalText(rssi: Int?): String {
    if (rssi == null) return "No reading"
    val percent = (((rssi + 90) / 50f).coerceIn(0f, 1f) * 100).roundToInt()
    val word = when {
        percent >= 66 -> "strong"
        percent >= 33 -> "medium"
        else -> "weak"
    }
    return "$rssi dBm, $word"
}

data class DashboardRanking(val active: List<SosRecord>, val cancelled: List<SosRecord>)

// Contract 2: a changed SOS arrives with a new message ID, so only the newest per victim is
// kept. Active ones are ranked by Contract 5; cancelled ones go last, most recent first.
fun rankForDashboard(records: List<SosRecord>, now: Long): DashboardRanking {
    val newest = records
        .groupBy { it.originId }
        .map { (_, copies) -> copies.maxBy { it.firstHeardAt } }
    return DashboardRanking(
        active = newest.filter { !it.cancelled }.sortedWith(PriorityScore.mostUrgentFirst(now)),
        cancelled = newest.filter { it.cancelled }.sortedByDescending { it.lastHeardAt },
    )
}

fun phoneLabel(id: Int): String = String.format(Locale.US, "%04X", id)

private val HEARD_AT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US).withZone(ZoneId.systemDefault())

private val HIGH_ORANGE = Color(0xFFE65100)
private val LISTENING_GREEN = Color(0xFF2E7D32)

private const val CLOCK_TICK_MS = 1_000L
private const val PULSE_MS = 900
private const val CANCELLED_ALPHA = 0.55f
private const val INFO_LABEL_WIDTH = 72
