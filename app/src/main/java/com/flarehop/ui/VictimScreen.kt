package com.flarehop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.FlareConstants
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus

private enum class MeshState { SEARCHING, JOINED, STARTED }

@Composable
fun VictimScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val engine = flare.engine
    val mySos by engine.mySos.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RadioBanner(state = rememberRadioState())
        if (mySos == null) {
            SosButton(onClick = { flare.sendSos(NO_DETAILS) }, modifier = Modifier.weight(1f))
        } else {
            MeshView(flare = flare, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = flare::cancelSos,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(text = "Cancel SOS", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SosButton(onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.size(SOS_BUTTON_SIZE.dp),
        ) {
            Text(
                text = "SOS",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Tap to call for help",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

// Phones never connect: sending the SOS is what joins or starts the mesh. Every survivor phone
// that hears it starts sending STANDBY and shows up in group, so an empty group after the
// search time means this phone is the first one here.
@Composable
private fun MeshView(flare: FlareViewModel, modifier: Modifier) {
    val engine = flare.engine
    val mySos by engine.mySos.collectAsState()
    val group by engine.group.collectAsState()
    val known by engine.knownSos.collectAsState()
    val gps by flare.locationReader.location.collectAsState()
    val now = rememberNow()

    val mine = mySos ?: return
    val record = mine.record
    val here = gps?.let { Position(it.latitude, it.longitude) }
        ?: record.latitude?.let { lat -> record.longitude?.let { Position(lat, it) } }

    val meshState = when {
        group.isNotEmpty() -> MeshState.JOINED
        now - record.firstHeardAt < SEARCH_MS -> MeshState.SEARCHING
        else -> MeshState.STARTED
    }

    val survivors = known
        .filter { !it.cancelled && it.originId != record.originId }
        .groupBy { it.originId }
        .map { (_, copies) -> copies.maxBy { it.firstHeardAt } }
        .sortedByDescending { it.lastHeardAt }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = when {
                    mine.status == SosStatus.HANDED_OFF -> "SOS handed on"
                    record.acked -> "A rescuer received your SOS"
                    else -> "SOS sent"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        // A rescuer's card is headed by the same ID, so the victim can tell them which one is theirs.
        item {
            Text(
                text = "You are phone ${phoneLabel(record.originId)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (mine.status == SosStatus.HANDED_OFF) {
            item {
                Text(
                    text = "Phone ${phoneLabel(record.carrierId)} keeps sending your SOS.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item { LowBatteryLine(mine.status, record.battery, phonesNearby = group.size) }
        item { MeshStatus(state = meshState, phones = group.size, rescuerHeard = record.acked) }
        // Other victims' SOS, not phones that heard this one, so the heading only shows when
        // there is one: "no other survivors" read as "nobody heard you" next to a rescuer's receipt.
        if (survivors.isNotEmpty()) {
            item {
                Text(
                    text = if (survivors.size == 1) {
                        "1 other SOS nearby"
                    } else {
                        "${survivors.size} other SOS nearby"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        items(survivors, key = { it.messageId }) { survivor ->
            SurvivorCard(record = survivor, here = here, now = now)
        }
    }
}

@Composable
// Rescuers send no STANDBY, so they never count as phones nearby; the receipt is how this phone
// knows one heard it.
private fun MeshStatus(state: MeshState, phones: Int, rescuerHeard: Boolean) {
    val (title, hint) = when {
        state == MeshState.JOINED ->
            (if (phones == 1) "Connected: 1 phone" else "Connected: $phones phones") to
                "These phones have your signal and can carry your SOS if your battery runs low."

        rescuerHeard ->
            "A rescuer has your signal" to
                "No other phone nearby to carry your SOS yet. Any Flarehop phone that comes " +
                "close joins by itself."

        state == MeshState.SEARCHING ->
            "Looking for a mesh nearby" to
                "Your SOS is going out. Phones that hear it join by themselves."

        else ->
            "You started a new mesh" to
                "No phone nearby yet. Any Flarehop phone that comes close joins and carries your SOS."
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LowBatteryLine(status: SosStatus, battery: Int?, phonesNearby: Int) {
    if (status != SosStatus.SENDING || battery == null) return
    if (battery >= FlareConstants.HANDOFF_BATTERY_PERCENT) return
    Text(
        text = if (phonesNearby > 0) {
            "Battery low. Handing your SOS to the phone nearby with the most battery."
        } else {
            "Battery low and no phone nearby can take over. Stay close to other phones."
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SurvivorCard(record: SosRecord, here: Position?, now: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Survivor ${phoneLabel(record.originId)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Location: ${locationText(record, here)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = "Sound: ${soundText(record)}", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Battery: ${record.battery?.let { "$it%" } ?: "Unknown"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Last signal ${((now - record.lastHeardAt) / 1_000).coerceAtLeast(0)}s ago",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// One tap sends the SOS with nothing to fill in; 0 people means "not given" (Contract 2).
private val NO_DETAILS = SosDetails(
    people = 0,
    injured = false,
    trapped = false,
    water = false,
    medical = false,
    fire = false,
)

// STANDBY goes out every 5 seconds, so three missed rounds means no phone is answering.
private const val SEARCH_MS = 15_000L
private const val SOS_BUTTON_SIZE = 220
