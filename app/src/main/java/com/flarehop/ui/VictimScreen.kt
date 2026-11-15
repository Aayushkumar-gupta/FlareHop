package com.flarehop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus
import java.util.Locale
import kotlin.math.roundToInt

private enum class HereSource { GPS, SOS_FIX, DEMO }

private data class Here(val latitude: Double, val longitude: Double, val source: HereSource)

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

    // Sample data sits around the demo point, so development always measures from there.
    val here = when {
        DEVELOPMENT -> Here(DemoLocation.LATITUDE, DemoLocation.LONGITUDE, HereSource.DEMO)

        currentGps != null ->
            Here(currentGps.latitude, currentGps.longitude, HereSource.GPS)

        sentLat != null && sentLon != null -> Here(sentLat, sentLon, HereSource.SOS_FIX)

        else -> null
    }

    // STANDBY packets carry no location (Contract 1), so group phones are placed by signal only.
    val groupBlips = group
        .filter { it.phoneId != myId && it.rssi != null }
        .map { member ->
            val role = if (member.carriedCount > 0) "Carrying ${member.carriedCount}" else "Phone"
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

        SosStatus.HANDED_OFF -> "Handed off"
    }

    val locationText = when (here?.source) {
        HereSource.GPS -> {
            val accuracy = currentGps?.takeIf { it.hasAccuracy() }?.accuracy?.roundToInt()
            if (accuracy != null) {
                "Your location: GPS, within $accuracy m."
            } else {
                "Your location: GPS."
            }
        }

        HereSource.SOS_FIX -> "Your location: from your SOS."

        HereSource.DEMO -> "Your location: demo coordinates."

        null -> "No GPS fix yet. Distances are estimated from Bluetooth signal."
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (mySos == null) {
                Button(
                    onClick = { flare.sendSos() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Text(text = "Send SOS", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = "Sends your location, the time and your battery level.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
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
            Text(
                text = "North is up. $locationText",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
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
