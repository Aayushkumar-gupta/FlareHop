package com.flarehop.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.core.content.ContextCompat
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

private enum class RadioState { READY, NO_PERMISSION, BLUETOOTH_OFF }

private data class RescuerPosition(val latitude: Double, val longitude: Double)

@Composable
fun SosListScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val role by flare.role.collectAsState()
    val radio = rememberRadioState()

    when {
        role != Role.RESCUER -> BecomeRescuer(onClick = flare::becomeRescuer, modifier = modifier)

        radio == RadioState.NO_PERMISSION -> RadioProblem(
            title = "Nearby devices is not allowed",
            hint = "Allow Nearby devices for Flarehop in Settings, then reopen the app.",
            onStop = flare::stopRescuing,
            modifier = modifier,
        )

        radio == RadioState.BLUETOOTH_OFF -> RadioProblem(
            title = "Bluetooth is off",
            hint = "Turn on Bluetooth to hear SOS signals.",
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
private fun RadioProblem(title: String, hint: String, onStop: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(text = hint, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        TextButton(onClick = onStop) { Text("Stop being a rescuer") }
    }
}





// Bluetooth can be switched off or the permission revoked while the app is open, so recheck.
@Composable
private fun rememberRadioState(): RadioState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(radioState(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(RADIO_CHECK_MS)
            state = radioState(context)
        }
    }
    return state
}

private fun radioState(context: Context): RadioState {
    if (DEVELOPMENT) return RadioState.READY
    val canScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED
    if (!canScan) return RadioState.NO_PERMISSION
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    return if (adapter?.isEnabled == true) RadioState.READY else RadioState.BLUETOOTH_OFF
}

// "Heard 12 s ago" and the time part of the priority score only move if something redraws.
@Composable
private fun rememberNow(): Long {
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
    val now = rememberNow()

    // Sample data sits around the demo point, so development measures from there.
    val currentGps = gps
    val here = when {
        DEVELOPMENT -> RescuerPosition(DemoLocation.LATITUDE, DemoLocation.LONGITUDE)
        currentGps != null -> RescuerPosition(currentGps.latitude, currentGps.longitude)
        else -> null
    }

    // Contract 2: a changed SOS arrives with a new message ID, so show only the newest per victim.
    val newest = records
        .groupBy { it.originId }
        .map { (_, copies) -> copies.maxBy { it.firstHeardAt } }
    val active = newest.filter { !it.cancelled }
        .sortedWith(PriorityScore.mostUrgentFirst(now))
    val cancelled = newest.filter { it.cancelled }.sortedByDescending { it.lastHeardAt }
    val bands = active.groupingBy { band(it, now) }.eachCount()

    Column(modifier.fillMaxSize()) {
        DashboardHeader(
            activeCount = active.size,
            bands = bands,
            onStop = flare::stopRescuing,
        )
        if (newest.isEmpty()) {
            EmptyDashboard()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(active, key = { _, it -> it.messageId }) { index, record ->
                    SosCard(record = record, rank = index + 1, now = now, here = here)
                }
                itemsIndexed(cancelled, key = { _, it -> it.messageId }) { _, record ->
                    SosCard(record = record, rank = null, now = now, here = here)
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(activeCount: Int, bands: Map<PriorityBand, Int>, onStop: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriorityBand.entries.forEach { band ->
                    BandCount(band = band, count = bands[band] ?: 0, modifier = Modifier.weight(1f))
                }
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
private fun BandCount(band: PriorityBand, count: Int, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = bandColor(band),
            )
            Text(text = bandName(band), style = MaterialTheme.typography.labelMedium)
        }
    }
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
private fun SosCard(record: SosRecord, rank: Int?, now: Long, here: RescuerPosition?) {
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
                    text = peopleText(record.people),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                FlagChips(record)
                HorizontalDivider()
                InfoRow("Sound", soundText(record))
                InfoRow("Location", locationText(record, here))
                InfoRow("Heard", heardText(record, now))
                InfoRow("Battery", record.battery?.let { "$it%" } ?: "Unknown")
                InfoRow("Signal", signalText(record.lastRssi))
                InfoRow("Carrier", carrierText(record))
                ReceiptLine(record)
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
private fun FlagChips(record: SosRecord) {
    val flags = flags(record)
    if (flags.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        flags.forEach { flag ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = flag,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
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

// This screen only shows on a rescuer phone, and the engine sends a receipt for every SOS it
// hears there. A rescuer never hears its own receipt, so acked here can only mean another
// rescuer answered too.
@Composable
private fun ReceiptLine(record: SosRecord) {
    val (text, color) = when {
        record.cancelled -> "Cancelled by the victim" to MaterialTheme.colorScheme.onSurfaceVariant
        record.acked -> "Receipt sent. Another rescuer answered too." to LISTENING_GREEN
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

private fun peopleText(people: Int) = when (people) {
    0 -> "People not given"
    1 -> "1 person"
    else -> "$people people"
}

private fun flags(record: SosRecord) = listOfNotNull(
    "Trapped".takeIf { record.trapped },
    "Injured".takeIf { record.injured },
    "Medical".takeIf { record.medical },
    "Water rising".takeIf { record.water },
    "Fire".takeIf { record.fire },
)

private fun soundText(record: SosRecord): String {
    val label = when (record.sound) {
        SoundType.NONE -> return "None heard"
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

private fun locationText(record: SosRecord, here: RescuerPosition?): String {
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

private fun compassPoint(bearing: Double): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return points[((bearing / 45.0).roundToInt()) % points.size]
}

// The packet has no timestamp field (Contract 1), so the time shown is when this phone first
// heard the SOS, which is within seconds of sending while the victim is in range.
private fun heardText(record: SosRecord, now: Long): String {
    val first = HEARD_AT.format(Instant.ofEpochMilli(record.firstHeardAt))
    val agoSec = ((now - record.lastHeardAt) / 1_000).coerceAtLeast(0)
    val last = when {
        agoSec < 60 -> "last ${agoSec}s ago"
        else -> "last ${agoSec / 60} min ago"
    }
    return "$first, $last"
}

// Same scale as Find mode (Contract 6): -90 dBm or weaker is 0%, -40 dBm or stronger is 100%.
private fun signalText(rssi: Int?): String {
    if (rssi == null) return "No reading"
    val percent = (((rssi + 90) / 50f).coerceIn(0f, 1f) * 100).roundToInt()
    val word = when {
        percent >= 66 -> "strong"
        percent >= 33 -> "medium"
        else -> "weak"
    }
    return "$rssi dBm, $word"
}

private fun carrierText(record: SosRecord): String {
    val origin = phoneLabel(record.originId)
    return if (record.carrierId == record.originId) {
        "Victim's own phone $origin"
    } else {
        "Phone ${phoneLabel(record.carrierId)}, handed off from $origin"
    }
}

private fun phoneLabel(id: Int) = String.format(Locale.US, "%04X", id)

private val HEARD_AT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US).withZone(ZoneId.systemDefault())

private val HIGH_ORANGE = Color(0xFFE65100)
private val LISTENING_GREEN = Color(0xFF2E7D32)

private const val RADIO_CHECK_MS = 2_000L
private const val CLOCK_TICK_MS = 1_000L
private const val PULSE_MS = 900
private const val CANCELLED_ALPHA = 0.55f
private const val INFO_LABEL_WIDTH = 72
