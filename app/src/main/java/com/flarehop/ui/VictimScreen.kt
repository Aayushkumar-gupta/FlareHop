package com.flarehop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus
import java.util.Locale

private data class Here(val latitude: Double, val longitude: Double)

@Composable
fun VictimScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val engine = flare.engine
    val mySos by engine.mySos.collectAsState()
    val group by engine.group.collectAsState()
    val known by engine.knownSos.collectAsState()
    val gps by flare.locationReader.location.collectAsState()

    val myRecord = mySos?.record
    val myId = myRecord?.originId
    val sentLat = myRecord?.latitude
    val sentLon = myRecord?.longitude
    val currentGps = gps

    val here = when {
        currentGps != null -> Here(currentGps.latitude, currentGps.longitude)
        sentLat != null && sentLon != null -> Here(sentLat, sentLon)
        else -> null
    }

    // STANDBY packets carry no location (Contract 1), so group phones are placed by signal only.
    val groupBlips = group
        .filter { it.phoneId != myId && it.rssi != null }
        .map { member ->
            val role = if (member.isCarrier) "Carrier" else "Standby"
            RadarBlip(
                phoneId = member.phoneId,
                label = "$role ${phoneLabel(member.phoneId)}",
                rssi = member.rssi,
            )
        }
    val sosBlips = known
        .filter { !it.cancelled && it.carrierId != myId }
        .map { sosBlip(it, here) }
    val blips = (groupBlips + sosBlips).distinctBy { it.phoneId }

    val statusText = when (mySos?.status) {
        null -> null

        SosStatus.SENDING ->
            if (myRecord?.acked == true) "A rescuer received your SOS" else "Sending"

        SosStatus.STANDING_BY -> "Standing by"

        SosStatus.HANDED_OFF -> "Handed off"
    }

    val sentWithoutFix = myRecord != null && (sentLat == null || sentLon == null)

    Box(modifier = modifier.fillMaxSize()) {
        RadarView(blips = blips, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                NorthCompass()
                NearbyPeople(count = blips.size)
            }
            if (statusText != null) {
                Text(text = statusText, style = MaterialTheme.typography.headlineSmall)
            }
            RadioBanner(state = rememberRadioState())
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (mySos == null) {
                SosPanel(onSend = flare::sendSos)
            } else {
                OutlinedButton(
                    onClick = { flare.cancelSos() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(text = "Cancel SOS", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (sentWithoutFix) {
                Text(
                    text = "Your SOS was sent without a location. " +
                        "Rescuers will find you by signal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SosPanel(onSend: (SosDetails) -> Unit) {
    // Slightly see-through so the radar behind it stays visible.
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    onSend(
                        SosDetails(
                            // 0 means "not given" (Contract 2); the screen no longer asks.
                            people = 0,
                            injured = false,
                            trapped = false,
                            water = false,
                            medical = false,
                            fire = false,
                        ),
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                Text(text = "Send SOS", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

// The radar is always drawn north-up, so the compass is fixed rather than following the phone.
@Composable
private fun NorthCompass() {
    val ring = MaterialTheme.colorScheme.outline
    val north = MaterialTheme.colorScheme.error
    val south = MaterialTheme.colorScheme.onSurfaceVariant
    val face = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "N",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = north,
        )
        Canvas(Modifier.size(COMPASS_SIZE.dp)) {
            val r = size.minDimension / 2f
            val half = r * 0.28f
            drawCircle(face, r, center)
            drawCircle(ring, r, center, style = Stroke(1.5.dp.toPx()))
            drawPath(needle(center, r * 0.8f, half, pointsUp = true), north)
            drawPath(needle(center, r * 0.8f, half, pointsUp = false), south)
            drawCircle(ring, 2.dp.toPx(), center)
        }
    }
}

private fun needle(center: Offset, length: Float, halfWidth: Float, pointsUp: Boolean) =
    Path().apply {
        val tipY = if (pointsUp) center.y - length else center.y + length
        moveTo(center.x, tipY)
        lineTo(center.x - halfWidth, center.y)
        lineTo(center.x + halfWidth, center.y)
        close()
    }

private const val COMPASS_SIZE = 44

// The plan says nothing pairs or connects, so the count is of phones heard nearby.
@Composable
private fun NearbyPeople(count: Int) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$count nearby",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        (1..minOf(count, MAX_PEOPLE_SHOWN)).forEach { n ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "Person $n",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        if (count > MAX_PEOPLE_SHOWN) {
            Text(
                text = "+${count - MAX_PEOPLE_SHOWN} more",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private const val MAX_PEOPLE_SHOWN = 3

// Real distance and direction need both positions; otherwise fall back to signal strength.
private fun sosBlip(record: SosRecord, here: Here?): RadarBlip {
    val lat = record.latitude
    val lon = record.longitude
    val label = "SOS ${phoneLabel(record.carrierId)}"
    if (here == null || lat == null || lon == null) {
        return RadarBlip(phoneId = record.carrierId, label = label, rssi = record.lastRssi)
    }
    return RadarBlip(
        phoneId = record.carrierId,
        label = label,
        rssi = record.lastRssi,
        distanceM = distanceMeters(here.latitude, here.longitude, lat, lon),
        bearingDeg = bearingDegrees(here.latitude, here.longitude, lat, lon),
    )
}

private fun phoneLabel(id: Int) = String.format(Locale.US, "%04X", id)
