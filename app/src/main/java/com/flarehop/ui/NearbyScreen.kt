package com.flarehop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flarehop.flare.Role
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus

// The mesh engine has no separate nearby role: every survivor phone near an SOS joins its group
// by itself and can take it over. This tab shows that side of the same phone, as a graph of what
// this phone knows: who it hears, who carries whose SOS, and which rescuer answered.
@Composable
fun NearbyScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val engine = flare.engine
    val role by flare.role.collectAsState()
    val mySos by engine.mySos.collectAsState()
    val known by engine.knownSos.collectAsState()
    val carried by engine.carried.collectAsState()
    val group by engine.group.collectAsState()
    val receipts by flare.receiptsBy.collectAsState()
    val now = rememberNow()

    val myId = mySos?.record?.originId ?: engine.phoneId
    val graph = MeshGraphBuilder.build(
        myId = myId,
        role = role,
        mySos = mySos,
        known = known,
        carried = carried,
        group = group,
        receiptsBy = receipts,
        now = now,
    )
    val activeSos = graph.others.count { it.kind == NodeKind.VICTIM }

    val status = when {
        role == Role.RESCUER -> "This phone is a rescuer"

        carried.isNotEmpty() ->
            "Carrying the SOS of " +
                carried.joinToString { "phone ${phoneLabel(it.originId)}" }

        mySos?.status == SosStatus.SENDING -> "Sending your own SOS"

        mySos?.status == SosStatus.HANDED_OFF -> "Your SOS is with another phone"

        activeSos == 0 -> "Looking for an SOS close by"

        else -> "$activeSos SOS close by"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RadioBanner(state = rememberRadioState()) }
        item {
            Column {
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "You are phone ${phoneLabel(myId)}. " + when (graph.others.size) {
                        0 -> "No other phone heard yet."
                        1 -> "1 other phone in the network."
                        else -> "${graph.others.size} other phones in the network."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { MeshGraphView(graph = graph) }
        item { GraphLegend() }
        if (graph.others.isNotEmpty()) {
            item {
                Text(text = "Phones", style = MaterialTheme.typography.titleLarge)
            }
            items(graph.others, key = { it.phoneId }) { node -> PhoneRow(node, known) }
        }
    }
}

// Phones never connect: each arrow is something this phone heard.
@Composable
private fun GraphLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(MaterialTheme.colorScheme.outline, dashed = false, "hears")
        LegendItem(CARRIER_ORANGE, dashed = true, "carries SOS")
        LegendItem(RESCUER_GREEN, dashed = false, "answered")
    }
}

@Composable
private fun LegendItem(color: Color, dashed: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 24.dp, height = 10.dp)) {
            val y = size.height / 2f
            drawLine(
                color,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 3.dp.toPx(),
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                } else {
                    null
                },
            )
        }
        Text(text = " $text", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PhoneRow(node: GraphNode, known: List<SosRecord>) {
    val part = when (node.kind) {
        NodeKind.RESCUER -> "rescuer"

        NodeKind.VICTIM -> "SOS"

        NodeKind.CARRIER ->
            "carries " + known
                .filter { it.carrierId == node.phoneId && it.originId != node.phoneId }
                .joinToString { phoneLabel(it.originId) }
                .ifEmpty { "an SOS" }

        else -> "phone"
    }
    val details = listOfNotNull(
        part,
        node.battery?.let { "$it%" },
        node.rssi?.let { "about ${distanceText(LocationEstimator.bluetooth(it).distanceM)}" },
    )
    Text(
        text = "${phoneLabel(node.phoneId)}  ${details.joinToString(", ")}",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

fun fixOf(record: SosRecord): GpsFix? {
    val lat = record.latitude ?: return null
    val lon = record.longitude ?: return null
    return GpsFix(lat, lon, record.accuracyM?.toDouble(), record.fixAgeMin ?: 0)
}
