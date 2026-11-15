package com.flarehop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class RadarBlip(
    val phoneId: Int,
    val label: String,
    val rssi: Int? = null,
    val distanceM: Double? = null,
    val bearingDeg: Double? = null,
)

@Composable
fun RadarView(blips: List<RadarBlip>, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outline
    val meColor = MaterialTheme.colorScheme.primary
    val blipColor = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    // The rings run past every edge of the screen; anything outside the view is clipped.
    Canvas(modifier.clipToBounds()) {
        val radius = hypot(size.width, size.height) / 2f * OVERSCAN
        val line = 1.dp.toPx()

        (1..RINGS).forEach { ring ->
            drawCircle(gridColor, radius * ring / RINGS, center, style = Stroke(line))
        }
        repeat(SPOKES) { i ->
            drawLine(gridColor, center, pointAt(center, radius, i * 360f / SPOKES), line)
        }

        drawCircle(meColor, 6.dp.toPx(), center)

        if (blips.isEmpty()) {
            val empty = textMeasurer.measure(
                "No one nearby",
                TextStyle(color = labelColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            )
            drawText(
                empty,
                topLeft = Offset(
                    center.x - empty.size.width / 2f,
                    center.y + 16.dp.toPx(),
                ),
            )
        }

        blips.forEach { blip ->
            val meters = blip.distanceM
            val bearing = blip.bearingDeg
            val rssi = blip.rssi
            val (far, angle) = when {
                meters != null && bearing != null ->
                    (meters / RANGE_M).toFloat().coerceIn(0f, 1f) to (bearing.toFloat() - 90f)

                rssi != null -> (1f - signalFraction(rssi)) to estimatedAngle(blip.phoneId)

                else -> return@forEach
            }
            val distance = radius * (MIN_BLIP_RADIUS + far * (1f - MIN_BLIP_RADIUS))
            // Keep every dot on screen even when its ring position is past the edge.
            val edge = EDGE_INSET.dp.toPx()
            val raw = pointAt(center, distance, angle)
            val position = Offset(
                raw.x.coerceIn(edge, size.width - edge),
                raw.y.coerceIn(edge, size.height - edge),
            )
            drawCircle(blipColor, 8.dp.toPx(), position)

            val label = textMeasurer.measure(
                "${blip.label}\n${distanceLabel(blip)}",
                TextStyle(color = labelColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            val gap = 12.dp.toPx()
            val labelX = if (position.x + gap + label.size.width > size.width) {
                position.x - gap - label.size.width
            } else {
                position.x + gap
            }
            val labelY = (position.y - label.size.height / 2f)
                .coerceIn(0f, size.height - label.size.height)
            drawText(label, topLeft = Offset(labelX, labelY))
        }
    }
}

private fun distanceLabel(blip: RadarBlip): String {
    val meters = blip.distanceM
    val rssi = blip.rssi
    return when {
        meters != null -> distanceText(meters)
        rssi != null -> "$rssi dBm"
        else -> "distance unknown"
    }
}

// Same scale as Find mode (Contract 6): -90 dBm or weaker is the edge,
// -40 dBm or stronger the center.
private fun signalFraction(rssi: Int) = ((rssi + 90) / 50f).coerceIn(0f, 1f)

// Bluetooth signal strength gives distance only, never direction, so each phone gets a fixed
// angle from its ID. Replace this once a real direction source exists.
private fun estimatedAngle(phoneId: Int) = (phoneId * GOLDEN_ANGLE_DEG) % 360f

private fun pointAt(center: Offset, distance: Float, angleDeg: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(
        center.x + distance * cos(rad).toFloat(),
        center.y + distance * sin(rad).toFloat(),
    )
}

// Phone Bluetooth rarely reaches this far, so any phone heard fits inside the outer ring.
private const val RANGE_M = 250.0

private const val SPOKES = 36
private const val RINGS = 5

// 1.0 puts the outer ring exactly at the screen corners; above 1.0 pushes it past them.
private const val OVERSCAN = 1.2f

// How far from the screen edge a dot is pinned when its real position is off screen.
private const val EDGE_INSET = 16
private const val MIN_BLIP_RADIUS = 0.15f
private const val GOLDEN_ANGLE_DEG = 137.508f
