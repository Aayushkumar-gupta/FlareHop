package com.flarehop.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import com.flarehop.sense.PriorityBand
import com.flarehop.sense.PriorityScore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private enum class RadioState { READY, NO_PERMISSION, BLUETOOTH_OFF }

@Composable
fun SosListScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val role by flare.role.collectAsState()
    val radio = rememberRadioState()

    when {
        role != Role.RESCUER -> BecomeRescuer(onClick = flare::becomeRescuer, modifier = modifier)

        radio == RadioState.NO_PERMISSION -> RadioProblem(
            title = "Nearby devices is not allowed",
            hint = "Allow Nearby devices for Flarehop in Settings, then reopen the app.",
            modifier = modifier,
        )

        radio == RadioState.BLUETOOTH_OFF -> RadioProblem(
            title = "Bluetooth is off",
            hint = "Turn on Bluetooth to hear SOS signals, then reopen the app.",
            modifier = modifier,
        )

        else -> SosList(engine = flare.engine, modifier = modifier)
    }
}

@Composable
private fun BecomeRescuer(onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Rescuer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "As a rescuer, this phone shows every SOS it hears and tells each victim " +
                "that help received it.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text(text = "Set yourself as rescuer", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun RadioProblem(title: String, hint: String, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
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

private const val RADIO_CHECK_MS = 2_000L

@Composable
private fun SosList(engine: FlareEngine, modifier: Modifier) {
    val records by engine.knownSos.collectAsState()
    // Contract 2: a changed SOS arrives with a new message ID, so show only the newest per victim.
    val shown = records
        .groupBy { it.originId }
        .map { (_, copies) -> copies.maxBy { it.firstHeardAt } }
        .sortedWith(PriorityScore.mostUrgentFirst(System.currentTimeMillis()))

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "SOS heard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (shown.size == 1) "1 victim nearby" else "${shown.size} victims nearby",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        if (shown.isEmpty()) {
            Text("Listening for SOS signals", style = MaterialTheme.typography.titleMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown, key = { it.messageId }) { SosCard(it) }
            }
        }
    }
}

@Composable
private fun SosCard(record: SosRecord) {
    val band = PriorityScore.band(PriorityScore.score(record, System.currentTimeMillis()))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = bandLabel(band),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = bandColor(band),
            )
            Text(
                text = peopleText(record.people),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val flags = flagsText(record)
            if (flags.isNotEmpty()) {
                Text(
                    text = flags,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(soundText(record), style = MaterialTheme.typography.bodyLarge)
            // The packet has no timestamp field (Contract 1), so the time shown is when this phone
            // first heard the SOS, which is within seconds of sending while the victim is in range.
            Text(
                text = "Heard ${HEARD_AT.format(Instant.ofEpochMilli(record.firstHeardAt))}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(fixText(record), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Battery ${record.battery?.let { "$it%" } ?: "unknown"} · " +
                    "Signal ${record.lastRssi?.let { "$it dBm" } ?: "no reading"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(carrierText(record), style = MaterialTheme.typography.bodyMedium)
            // This screen only shows on a rescuer phone, and the engine sends a receipt for every
            // SOS it hears there. A rescuer never hears its own receipt, so acked here can only
            // mean another rescuer answered too.
            Text(
                text = when {
                    record.cancelled -> "Cancelled by the victim"
                    record.acked -> "Receipt sent. Another rescuer answered too."
                    else -> "Receipt sent"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun peopleText(people: Int) = when (people) {
    0 -> "People: not given"
    1 -> "1 person"
    else -> "$people people"
}

private fun bandLabel(band: PriorityBand) = when (band) {
    PriorityBand.CRITICAL -> "CRITICAL"
    PriorityBand.HIGH -> "HIGH"
    PriorityBand.NORMAL -> "NORMAL"
}

// Contract 5 colours: Critical red, High orange, Normal plain.
@Composable
private fun bandColor(band: PriorityBand): Color = when (band) {
    PriorityBand.CRITICAL -> MaterialTheme.colorScheme.error
    PriorityBand.HIGH -> HIGH_ORANGE
    PriorityBand.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val HIGH_ORANGE = Color(0xFFE65100)

private val HEARD_AT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US).withZone(ZoneId.systemDefault())

private fun flagsText(record: SosRecord) = listOfNotNull(
    "Trapped".takeIf { record.trapped },
    "Injured".takeIf { record.injured },
    "Medical".takeIf { record.medical },
    "Water rising".takeIf { record.water },
    "Fire".takeIf { record.fire },
).joinToString(", ")

private fun soundText(record: SosRecord): String {
    val label = when (record.sound) {
        SoundType.NONE -> return "No sound heard"
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

private fun fixText(record: SosRecord): String {
    val lat = record.latitude
    val lon = record.longitude
    if (lat == null || lon == null) return "No GPS fix"
    val place = String.format(Locale.US, "%.5f, %.5f", lat, lon)
    val accuracy = record.accuracyM?.let { " ±$it m" } ?: ""
    val age = record.fixAgeMin?.let { if (it == 0) ", just now" else ", $it min old" } ?: ""
    return "$place$accuracy$age"
}

private fun carrierText(record: SosRecord): String {
    val origin = phoneLabel(record.originId)
    return if (record.carrierId == record.originId) {
        "Carried by the victim's phone $origin"
    } else {
        "Carried by nearby phone ${phoneLabel(record.carrierId)}, handed off from $origin"
    }
}

private fun phoneLabel(id: Int) = String.format(Locale.US, "%04X", id)
