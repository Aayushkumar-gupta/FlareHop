package com.flarehop.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flarehop.flare.FlareConstants
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus
import com.flarehop.sense.HeardSound
import com.flarehop.sense.SignsOfLife
import com.flarehop.ui.theme.Bronze
import com.flarehop.ui.theme.BronzeDeep
import com.flarehop.ui.theme.BronzeDim
import com.flarehop.ui.theme.Ember
import com.flarehop.ui.theme.EmberCore
import com.flarehop.ui.theme.EmberDeep
import com.flarehop.ui.theme.Inscription
import com.flarehop.ui.theme.Mist
import com.flarehop.ui.theme.NightSky
import com.flarehop.ui.theme.Parchment

private enum class MeshState { SEARCHING, JOINED, STARTED }

@Composable
fun VictimScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val engine = flare.engine
    val mySos by engine.mySos.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        NightSky()
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "FLAREHOP",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 6.sp,
                color = BronzeDim,
            )
            RadioBanner(state = rememberRadioState())
            if (mySos == null) {
                SosButton(
                    phoneId = engine.phoneId,
                    onClick = { flare.sendSos(NO_DETAILS) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                MeshView(flare = flare, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = flare::cancelSos,
                    border = BorderStroke(1.dp, BronzeDim),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(text = "Cancel SOS", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// A signal fire: an ember core with rings of light going out from it, the way a beacon on a
// headland was seen from the sea. Still a plain round SOS button, as big as before.
@Composable
private fun SosButton(phoneId: Int, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            BeaconRings(Modifier.size(GLOW_SIZE.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(SOS_BUTTON_SIZE.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(EmberCore, Ember, EmberDeep)))
                    .border(2.dp, Bronze, CircleShape)
                    .clickable(onClickLabel = "Send SOS", role = Role.Button, onClick = onClick),
            ) {
                Text(
                    text = "SOS",
                    style = MaterialTheme.typography.displayLarge,
                    color = Parchment,
                )
            }
        }
        Text(text = "LIGHT THE BEACON", style = Inscription, color = Bronze)
        Text(
            text = "Tap once to call for help. Your phone keeps calling, even with the screen off.",
            style = MaterialTheme.typography.bodyLarge,
            color = Mist,
            textAlign = TextAlign.Center,
        )
        // The code this phone sends and rescuers see on its card, so the owner knows it before
        // they ever need to call for help.
        Text(
            text = "You are phone ${phoneLabel(phoneId)}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Parchment,
        )
    }
}

// Two rings of firelight that grow and fade, half a cycle apart.
@Composable
private fun BeaconRings(modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "beacon").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing)),
        label = "beacon pulse",
    )
    Canvas(modifier) {
        val inner = SOS_BUTTON_SIZE.dp.toPx() / 2f
        val outer = size.minDimension / 2f
        listOf(pulse, (pulse + 0.5f) % 1f).forEach { phase ->
            drawCircle(
                color = Ember.copy(alpha = (1f - phase) * 0.45f),
                radius = inner + (outer - inner) * phase,
                center = center,
                style = Stroke(width = (6f - 4f * phase).dp.toPx()),
            )
        }
    }
}

// Shown while this phone sends its SOS: a small fire that keeps glowing, so the victim can see at
// a glance that the beacon is still lit.
@Composable
private fun BurningBeacon() {
    val glow by rememberInfiniteTransition(label = "burning").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS / 2), RepeatMode.Reverse),
        label = "burning glow",
    )
    Canvas(Modifier.size(SMALL_BEACON_SIZE.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            Brush.radialGradient(
                listOf(Ember.copy(alpha = 0.45f * glow), Ember.copy(alpha = 0f)),
                center,
                r,
            ),
            r,
            center,
        )
        drawCircle(
            Brush.radialGradient(listOf(EmberCore, Ember, EmberDeep), center, r * 0.55f),
            r * 0.55f * (0.85f + 0.15f * glow),
            center,
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
    val names by flare.names.collectAsState()
    val listening by flare.listening.collectAsState()
    val heard by flare.heardSound.collectAsState()
    val now = rememberNow()

    val mine = mySos ?: return
    val record = mine.record

    val meshState = when {
        group.isNotEmpty() -> MeshState.JOINED
        now - record.firstHeardAt < SEARCH_MS -> MeshState.SEARCHING
        else -> MeshState.STARTED
    }

    val survivors = known
        .filter { !it.cancelled && it.originId != record.originId }
        .groupBy { it.originId }
        .map { (_, copies) -> copies.maxBy { it.firstHeardAt } }
        // Survivor 1, 2, 3 in the order first heard. Sorting by last heard would reshuffle the
        // cards with every packet.
        .sortedBy { names.survivorNumber(it.originId) ?: Int.MAX_VALUE }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { BurningBeacon() }
        // An epic line for the moment, then the plain words for anyone who needs them.
        item {
            Text(
                text = when {
                    mine.status == SosStatus.HANDED_OFF -> "YOUR BEACON PASSED ON"
                    record.acked -> "A RESCUER SAW YOUR FIRE"
                    else -> "YOUR BEACON IS BURNING"
                },
                style = Inscription,
                color = Bronze,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = when {
                    mine.status == SosStatus.HANDED_OFF -> "SOS handed on"
                    record.acked -> "A rescuer received your SOS"
                    else -> "SOS sent"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Ember,
                textAlign = TextAlign.Center,
            )
        }
        // A rescuer's card is headed by the same ID, so the victim can tell them which one is theirs.
        item {
            Text(
                text = "You are phone ${phoneLabel(record.originId)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Parchment,
                textAlign = TextAlign.Center,
            )
        }
        if (mine.status == SosStatus.HANDED_OFF) {
            item {
                Text(
                    text = "${names.phoneWithCode(record.carrierId)} keeps sending your SOS.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (mine.status == SosStatus.SENDING) {
            item {
                HeardHere(listening = listening, heard = heard, battery = record.battery, now = now)
            }
        }
        item { LowBatteryLine(mine.status, record.battery, phonesNearby = group.size) }
        item { MeshStatus(state = meshState, phones = group.size, rescuerHeard = record.acked) }
        // Other victims' SOS, not phones that heard this one, so the line only shows when there is
        // one: "no other survivors" read as "nobody heard you" next to a rescuer's receipt.
        if (survivors.isNotEmpty()) {
            item {
                Text(
                    text = if (survivors.size == 1) {
                        "1 other SOS nearby"
                    } else {
                        "${survivors.size} other SOS nearby"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Mist,
                )
            }
        }
    }
}

@Composable
// Rescuers send no STANDBY, so they never count as phones nearby; the receipt is how this phone
// knows one heard it.
private fun MeshStatus(state: MeshState, phones: Int, rescuerHeard: Boolean) {
    Text(
        text = when {
            state == MeshState.JOINED && phones == 1 -> "1 phone nearby can carry your SOS"
            state == MeshState.JOINED -> "$phones phones nearby can carry your SOS"
            rescuerHeard -> "A rescuer has your signal"
            state == MeshState.SEARCHING -> "Looking for phones nearby"
            else -> "No phone nearby yet. Any that comes close joins by itself."
        },
        style = MaterialTheme.typography.titleMedium,
        color = Mist,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HeardHere(listening: Boolean, heard: HeardSound?, battery: Int?, now: Long) {
    val context = LocalContext.current
    val micAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = heard?.let {
                "Heard here: ${soundLabel(it.type)}, ${secondsAgoText((now - it.atMs) / 1_000L)}"
            } ?: "No sound heard yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = micText(allowed = micAllowed, listening = listening, battery = battery),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// The listener only reports itself on once the sound model has loaded, about a second after the
// SOS, so "not listening" alone does not mean the permission is missing.
private fun micText(allowed: Boolean, listening: Boolean, battery: Int?): String = when {
    !allowed -> "Microphone not allowed. Allow it in Settings so rescuers hear your knocks."

    battery != null && battery < SignsOfLife.STOP_BELOW_PERCENT ->
        "Microphone off below ${SignsOfLife.STOP_BELOW_PERCENT}% battery, to keep your SOS going."

    !listening -> "Starting the microphone..."

    else -> "Listening for knocks and voices. Rescuers see what this phone hears."
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
private const val GLOW_SIZE = 300
private const val SMALL_BEACON_SIZE = 72
private const val PULSE_MS = 2_400
