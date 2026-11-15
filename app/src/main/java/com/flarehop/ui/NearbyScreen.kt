package com.flarehop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.flarehop.flare.GroupMember
import com.flarehop.flare.Role
import com.flarehop.flare.SosRecord
import com.flarehop.flare.SosStatus
import java.util.Locale

// The mesh engine has no separate nearby role: every survivor phone near an SOS joins its group
// by itself and can take it over. This tab shows that side of the same phone, as a radar.
@Composable
fun NearbyScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val engine = flare.engine
    val role by flare.role.collectAsState()
    val mySos by engine.mySos.collectAsState()
    val known by engine.knownSos.collectAsState()
    val carried by engine.carried.collectAsState()
    val group by engine.group.collectAsState()
    val gps by flare.locationReader.location.collectAsState()

    val myRecord = mySos?.record
    val myId = myRecord?.originId
    val here = gps?.let {
        GpsFix(
            latitude = it.latitude,
            longitude = it.longitude,
            accuracyM = if (it.hasAccuracy()) it.accuracy.toDouble() else null,
            ageMin = ((System.currentTimeMillis() - it.time) / 60_000L).toInt().coerceAtLeast(0),
        )
    } ?: myRecord?.let(::fixOf)

    val activeSos = known.filter { !it.cancelled && it.originId != myId }
    // Group phones send no location, so they are placed by signal only.
    val phoneBlips = group.mapNotNull { member ->
        val bluetooth = member.rssi?.let(LocationEstimator::bluetooth)
        LocationEstimator.estimate(gps = null, bluetooth = bluetooth)?.let {
            RadarBlip(
                phoneId = member.phoneId,
                label = phoneText(member),
                estimate = it,
                isSos = false,
            )
        }
    }
    val sosBlips = activeSos.mapNotNull { sosBlip(it, here) }
    val blips = (sosBlips + phoneBlips).distinctBy { it.phoneId }

    val status = when {
        role == Role.RESCUER -> "This phone is a rescuer"

        carried.isNotEmpty() ->
            "Carrying the SOS of " +
                carried.joinToString { "phone ${phoneLabel(it.originId)}" }

        mySos?.status == SosStatus.SENDING -> "Sending your own SOS"

        mySos?.status == SosStatus.HANDED_OFF -> "Your SOS is with another phone"

        activeSos.isEmpty() -> "Looking for an SOS close by"

        else -> "${activeSos.distinctBy { it.originId }.size} SOS close by"
    }

    Box(modifier = modifier.fillMaxSize()) {
        RadarView(blips = blips, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RadioBanner(state = rememberRadioState())
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                NorthCompass()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (role != Role.RESCUER) {
                            Text(
                                text = if (group.size == 1) {
                                    "1 phone in this group"
                                } else {
                                    "${group.size} phones in this group"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
        RadarLegend(Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun RadarLegend(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = modifier,
    ) {
        Text(
            text = "● best estimate   ○ direction unknown\n" +
                "□ GPS position   - - - Bluetooth distance",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// The radar is always drawn north-up, so the compass is fixed rather than following the phone.
@Composable
private fun NorthCompass() {
    val ring = MaterialTheme.colorScheme.outline
    val north = MaterialTheme.colorScheme.error
    val south = MaterialTheme.colorScheme.onSurfaceVariant
    val face = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "N",
            style = MaterialTheme.typography.titleMedium,
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

private const val COMPASS_SIZE = 52

private fun sosBlip(record: SosRecord, here: GpsFix?): RadarBlip? {
    val gps = here?.let { h -> fixOf(record)?.let { LocationEstimator.gps(h, it) } }
    val handedOff = record.carrierId != record.originId
    // After a handoff the GPS point is still the victim's but the signal comes from the carrier,
    // which may be somewhere else, so the two only mix while the victim's own phone is sending.
    val bluetooth = record.lastRssi
        ?.takeIf { !handedOff || gps == null }
        ?.let(LocationEstimator::bluetooth)
    val estimate = LocationEstimator.estimate(gps, bluetooth) ?: return null
    val carrier = if (handedOff) " via ${phoneLabel(record.carrierId)}" else ""
    return RadarBlip(
        phoneId = record.originId,
        label = "SOS ${phoneLabel(record.originId)}$carrier",
        estimate = estimate,
        isSos = true,
    )
}

private fun fixOf(record: SosRecord): GpsFix? {
    val lat = record.latitude ?: return null
    val lon = record.longitude ?: return null
    return GpsFix(lat, lon, record.accuracyM?.toDouble(), record.fixAgeMin ?: 0)
}

private fun phoneText(member: GroupMember): String {
    val battery = member.battery?.let { ", $it%" } ?: ""
    return "Phone ${phoneLabel(member.phoneId)}$battery"
}

private fun phoneLabel(id: Int) = String.format(Locale.US, "%04X", id)
