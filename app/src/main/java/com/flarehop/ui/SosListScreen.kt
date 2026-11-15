package com.flarehop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SoundType
import java.util.Locale

@Composable
fun SosListScreen(engine: FlareEngine, modifier: Modifier = Modifier) {
    val records by engine.knownSos.collectAsState()
    // Contract 2: a changed SOS arrives with a new message ID, so show only the newest per victim.
    val shown = records
        .groupBy { it.originId }
        .map { (_, copies) -> copies.maxBy { it.firstHeardAt } }
        .sortedBy { it.firstHeardAt }

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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Text(fixText(record), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Battery ${record.battery?.let { "$it%" } ?: "unknown"} · " +
                    "Signal ${record.lastRssi?.let { "$it dBm" } ?: "no reading"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(carrierText(record), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = when {
                    record.cancelled -> "Cancelled by the victim"
                    record.acked -> "Receipt sent"
                    else -> "No receipt yet"
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
