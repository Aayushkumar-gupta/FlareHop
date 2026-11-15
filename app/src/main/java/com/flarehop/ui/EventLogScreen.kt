package com.flarehop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import com.flarehop.flare.LogEvent
import com.flarehop.flare.LogType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EventLogScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val events by flare.events.collectAsState()

    Column(modifier.fillMaxSize()) {
        Text(
            text = "Event log",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )
        if (events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Nothing yet. SOS heard, receipts, handoffs and cancels show up " +
                        "here, newest first.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(events) { event ->
                    EventRow(event)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: LogEvent) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = TIME.format(Instant.ofEpochMilli(event.atMs)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(TIME_WIDTH.dp),
        )
        Column {
            Text(
                text = eventText(event.type),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = eventDetail(event),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun eventText(type: LogType) = when (type) {
    LogType.HEARD -> "SOS heard"
    LogType.DROPPED -> "Packet dropped"
    LogType.JOINED -> "Phone joined"
    LogType.HANDOFF_SENT -> "Handoff sent"
    LogType.TOOK_OVER -> "Took over"
    LogType.BACKED_OFF -> "Backed off"
    LogType.RECEIPT_SENT -> "Receipt sent"
    LogType.RECEIPT_MATCHED -> "Receipt received"
    LogType.CANCELLED -> "SOS cancelled"
}

private fun eventDetail(event: LogEvent): String {
    val reason = event.reason
    if (event.type == LogType.DROPPED) return "Reason: ${reason ?: "unknown"}"
    return String.format(
        Locale.US,
        "SOS %08X, phone %04X",
        event.messageId,
        event.phoneId,
    )
}

private val TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US).withZone(ZoneId.systemDefault())

private const val TIME_WIDTH = 84
