package com.flarehop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

@Composable
fun VictimScreen(engine: FlareEngine, modifier: Modifier = Modifier) {
    val mySos by engine.mySos.collectAsState()
    val group by engine.group.collectAsState()
    val known by engine.knownSos.collectAsState()

    val myRecord = mySos?.record
    val myLat = myRecord?.latitude
    val myLon = myRecord?.longitude
    val usingDemo = myLat == null || myLon == null
    val hereLat = myLat ?: DemoLocation.LATITUDE
    val hereLon = myLon ?: DemoLocation.LONGITUDE
    val myId = myRecord?.originId

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
        .map { sosBlip(it, hereLat, hereLon) }
    val blips = (groupBlips + sosBlips).distinctBy { it.phoneId }

    val statusText = when (mySos?.status) {
        null -> null

        SosStatus.SENDING ->
            if (myRecord?.acked == true) "A rescuer received your SOS" else "Sending"

        SosStatus.STANDING_BY -> "Standing by"

        SosStatus.HANDED_OFF -> "Handed off"
    }

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
                Text(
                    text = "SOS",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                ConnectedPeople(count = blips.size)
            }
            if (statusText != null) {
                Text(text = statusText, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            text = if (usingDemo) {
                "North is up. Your location: demo coordinates."
            } else {
                "North is up. Your location: GPS."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Composable
private fun ConnectedPeople(count: Int) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$count connected",
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

private fun sosBlip(record: SosRecord, hereLat: Double, hereLon: Double): RadarBlip {
    val lat = record.latitude
    val lon = record.longitude
    val label = "SOS ${phoneLabel(record.carrierId)}"
    if (lat == null || lon == null) {
        return RadarBlip(phoneId = record.carrierId, label = label, rssi = record.lastRssi)
    }
    return RadarBlip(
        phoneId = record.carrierId,
        label = label,
        rssi = record.lastRssi,
        distanceM = distanceMeters(hereLat, hereLon, lat, lon),
        bearingDeg = bearingDegrees(hereLat, hereLon, lat, lon),
    )
}

private fun phoneLabel(id: Int) = String.format(Locale.US, "%04X", id)
