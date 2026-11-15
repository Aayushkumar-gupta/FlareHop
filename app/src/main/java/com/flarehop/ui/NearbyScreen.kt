package com.flarehop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus
import java.util.Locale

// The engine fills knownSos for every SOS heard on any role, while mySos and group only change
// once the standby rules exist, so this screen is driven by knownSos.
@Composable
fun NearbyScreen(engine: FlareEngine, modifier: Modifier = Modifier) {
    val mySos by engine.mySos.collectAsState()
    val known by engine.knownSos.collectAsState()
    val now = rememberNow()

    val inRange = known
        .filter { !it.cancelled && now - it.lastHeardAt <= IN_RANGE_MS }
        .groupBy { it.originId }
        .map { (_, copies) -> copies.maxBy { it.firstHeardAt } }
        .sortedByDescending { it.lastRssi ?: Int.MIN_VALUE }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RadioBanner(state = rememberRadioState())
        Text(
            text = "Nearby phone",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when {
                mySos?.status == SosStatus.STANDING_BY -> "Backing up an SOS"
                mySos?.status == SosStatus.SENDING -> "Carrying an SOS"
                mySos?.status == SosStatus.HANDED_OFF -> "SOS handed to another phone"
                inRange.isEmpty() -> "Looking for an SOS close by"
                inRange.size == 1 -> "1 SOS close by"
                else -> "${inRange.size} SOS close by"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Keep this phone close to the person who needs help.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(inRange, key = { it.messageId }) { record ->
                NearbySosCard(record = record, now = now)
            }
        }
    }
}

@Composable
private fun NearbySosCard(record: SosRecord, now: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "SOS from phone ${String.format(Locale.US, "%04X", record.originId)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(text = peopleText(record.people), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Signal: ${signalText(record.lastRssi)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Heard ${((now - record.lastHeardAt) / 1_000).coerceAtLeast(0)}s ago",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// An SOS phone advertises several times a second, so silence this long means it is out of range.
private const val IN_RANGE_MS = 15_000L
